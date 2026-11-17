package com.thinklab.infrastructure.adapter.out.persistence;

import com.thinklab.application.dto.request.CaptureDecisionRequest;
import com.thinklab.application.usecase.CancelApprovalRequestUseCase;
import com.thinklab.application.usecase.CaptureDecisionUseCase;
import com.thinklab.domain.exception.InvalidApprovalRequestStatusException;
import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import com.thinklab.domain.repository.ApprovalRequestRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The cancel races against a real MongoDB (platform race audit, ACTION-PLAN 0.5): a request cancelled while its last approval is being recorded must not
 * end up CANCELLED after the caller was told APPROVED, and ten cancellations of one request must have exactly one winner.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CancelRaceIT implements TestPropertyProvider {

    private static final String DATABASE = "workflow_approval_race_it";
    private static final String EXECUTOR = "op-1";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject ApprovalRequestRepository requests;
    @Inject CancelApprovalRequestUseCase cancel;
    @Inject CaptureDecisionUseCase decide;

    private ApprovalRequest pending(UUID approver) {
        ApprovalRequest request = ApprovalRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), "ChangeRequest", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, List.of(approver), EXECUTOR);
        requests.create(request).block();
        return request;
    }

    @Test
    @DisplayName("ten cancellations of one request at once: exactly one wins and the others are refused with a conflict")
    void tenCancellationsAtOnce() {
        ApprovalRequest request = pending(UUID.randomUUID());

        List<String> results = Flux.range(0, 10)
                .flatMap(i -> cancel.execute(request.getId(), request.getOrganisationId(), EXECUTOR).thenReturn("OK")
                        .onErrorResume(e -> Mono.just(e.getClass().getSimpleName())), 10)
                .collectList().block(Duration.ofSeconds(30));

        assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
        assertEquals(9, results.stream().filter(InvalidApprovalRequestStatusException.class.getSimpleName()::equals).count(), results.toString());
        ApprovalRequest stored = requests.findById(request.getId(), request.getOrganisationId()).block();
        assertEquals(ApprovalStatus.CANCELLED, stored.getStatus());
        assertEquals(2, stored.getAuditTrail().size(), "one entry for creating and one for the single cancel that won");
    }

    @RepeatedTest(5)
    @DisplayName("a cancel and the last approval at once: one wins, and the stored status is the winner's, never CANCELLED after APPROVED was answered")
    void cancelAgainstTheLastApproval() {
        UUID approver = UUID.randomUUID();
        ApprovalRequest request = pending(approver);

        List<String> results = Flux.just("CANCEL", "APPROVE")
                .flatMap(action -> ("CANCEL".equals(action)
                        ? cancel.execute(request.getId(), request.getOrganisationId(), EXECUTOR).thenReturn("CANCELLED")
                        : decide.execute(request.getId(), request.getOrganisationId(), approver, new CaptureDecisionRequest(DecisionOutcome.APPROVE, "ok"), EXECUTOR)
                                .map(r -> r.status()))
                        .onErrorResume(e -> Mono.just("REFUSED")), 2)
                .collectList().block(Duration.ofSeconds(30));

        ApprovalRequest stored = requests.findById(request.getId(), request.getOrganisationId()).block();
        long winners = results.stream().filter(r -> !"REFUSED".equals(r)).count();
        assertEquals(1, winners, results.toString());
        String told = results.stream().filter(r -> !"REFUSED".equals(r)).findFirst().get();
        assertEquals(told, stored.getStatus().name(), "the caller was told " + told + " and the stored status is " + stored.getStatus());
    }
}
