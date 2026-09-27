package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CaptureDecisionRequest;
import com.thinklab.application.dto.request.InitiateApprovalRequestRequest;
import com.thinklab.domain.exception.ApprovalPolicyNotFoundException;
import com.thinklab.domain.exception.ApprovalRequestNotFoundException;
import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ApprovalPolicyRepository;
import com.thinklab.domain.repository.ApprovalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalRequestUseCaseTest {

    private static final String EXECUTOR = "op-1";

    @Mock private HashServicePort hashServicePort;
    @Mock private ApprovalPolicyRepository policyRepository;
    @Mock private ApprovalRequestRepository requestRepository;

    private UUID organisationId;
    private UUID approverA;
    private ApprovalPolicy policy;
    private ApprovalRequest request;

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        approverA = UUID.randomUUID();
        policy = ApprovalPolicy.createNew(UUID.randomUUID(), organisationId, "CAB", 1, List.of(approverA));
        request = ApprovalRequest.createNew(UUID.randomUUID(), organisationId, "ChangeRequest", UUID.randomUUID(),
                UUID.randomUUID(), policy.getId(), 1, List.of(approverA), EXECUTOR);
    }

    // --- InitiateApprovalRequestUseCase ---

    @Test
    @DisplayName("initiate: an unknown policy surfaces ApprovalPolicyNotFoundException")
    void initiatePolicyNotFound() {
        InitiateApprovalRequestRequest body = new InitiateApprovalRequestRequest("ChangeRequest", UUID.randomUUID(), UUID.randomUUID(), policy.getId());
        when(policyRepository.findById(policy.getId())).thenReturn(Mono.empty());
        InitiateApprovalRequestUseCase useCase = new InitiateApprovalRequestUseCase(hashServicePort, policyRepository, requestRepository);

        StepVerifier.create(useCase.execute(organisationId, body, EXECUTOR)).expectError(ApprovalPolicyNotFoundException.class).verify();
    }

    @Test
    @DisplayName("initiate: loads the policy, snapshots its quorum, fetches a sovereign id and persists the request")
    void initiateSuccess() {
        InitiateApprovalRequestRequest body = new InitiateApprovalRequestRequest("ChangeRequest", UUID.randomUUID(), UUID.randomUUID(), policy.getId());
        when(policyRepository.findById(policy.getId())).thenReturn(Mono.just(policy));
        when(hashServicePort.generateSovereignId("approval-request-creation")).thenReturn(Mono.just(request.getId()));
        when(requestRepository.create(any(ApprovalRequest.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        InitiateApprovalRequestUseCase useCase = new InitiateApprovalRequestUseCase(hashServicePort, policyRepository, requestRepository);

        StepVerifier.create(useCase.execute(organisationId, body, EXECUTOR))
                .assertNext(response -> assertEquals(policy.getRequiredApprovals(), response.requiredApprovals()))
                .verifyComplete();
    }

    // --- RetrieveApprovalRequestUseCase ---

    @Test
    @DisplayName("retrieveById: not found surfaces ApprovalRequestNotFoundException")
    void retrieveByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(requestRepository.findById(id)).thenReturn(Mono.empty());
        RetrieveApprovalRequestUseCase useCase = new RetrieveApprovalRequestUseCase(requestRepository);

        StepVerifier.create(useCase.execute(id)).expectError(ApprovalRequestNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieveById: maps the found request to a response")
    void retrieveByIdFound() {
        when(requestRepository.findById(request.getId())).thenReturn(Mono.just(request));
        RetrieveApprovalRequestUseCase useCase = new RetrieveApprovalRequestUseCase(requestRepository);

        StepVerifier.create(useCase.execute(request.getId())).expectNextCount(1).verifyComplete();
    }

    // --- RetrieveApprovalRequestsUseCase ---

    @Test
    @DisplayName("retrieveAll: delegates to the repository's tenant-scoped, optionally filtered query")
    void retrieveAll() {
        when(requestRepository.findAllByOrganisationId(organisationId, "ChangeRequest", request.getSubjectId(), ApprovalStatus.PENDING))
                .thenReturn(Flux.just(request));
        RetrieveApprovalRequestsUseCase useCase = new RetrieveApprovalRequestsUseCase(requestRepository);

        StepVerifier.create(useCase.execute(organisationId, "ChangeRequest", request.getSubjectId(), ApprovalStatus.PENDING))
                .expectNextCount(1).verifyComplete();
    }

    // --- CaptureDecisionUseCase ---

    @Test
    @DisplayName("captureDecision: not found surfaces ApprovalRequestNotFoundException")
    void captureDecisionNotFound() {
        UUID id = UUID.randomUUID();
        when(requestRepository.findById(id)).thenReturn(Mono.empty());
        CaptureDecisionUseCase useCase = new CaptureDecisionUseCase(requestRepository);

        StepVerifier.create(useCase.execute(id, approverA, new CaptureDecisionRequest(DecisionOutcome.APPROVE, "ok"), EXECUTOR))
                .expectError(ApprovalRequestNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("captureDecision: applies the domain decision, persists it and returns the resolved response")
    void captureDecisionSuccess() {
        when(requestRepository.findById(request.getId())).thenReturn(Mono.just(request));
        lenient().when(requestRepository.addDecision(any(), any(), any(), any())).thenReturn(Mono.empty());
        CaptureDecisionUseCase useCase = new CaptureDecisionUseCase(requestRepository);

        StepVerifier.create(useCase.execute(request.getId(), approverA, new CaptureDecisionRequest(DecisionOutcome.APPROVE, "ok"), EXECUTOR))
                .assertNext(response -> assertEquals("APPROVED", response.status()))
                .verifyComplete();
    }

    // --- CancelApprovalRequestUseCase ---

    @Test
    @DisplayName("cancel: not found surfaces ApprovalRequestNotFoundException")
    void cancelNotFound() {
        UUID id = UUID.randomUUID();
        when(requestRepository.findById(id)).thenReturn(Mono.empty());
        CancelApprovalRequestUseCase useCase = new CancelApprovalRequestUseCase(requestRepository);

        StepVerifier.create(useCase.execute(id, EXECUTOR)).expectError(ApprovalRequestNotFoundException.class).verify();
    }

    @Test
    @DisplayName("cancel: applies the domain transition and persists the granular status update")
    void cancelSuccess() {
        when(requestRepository.findById(request.getId())).thenReturn(Mono.just(request));
        when(requestRepository.updateStatus(any(), any(), any())).thenReturn(Mono.empty());
        CancelApprovalRequestUseCase useCase = new CancelApprovalRequestUseCase(requestRepository);

        StepVerifier.create(useCase.execute(request.getId(), EXECUTOR)).verifyComplete();
    }

    // --- RetrieveApprovalAuditLogUseCase ---

    @Test
    @DisplayName("auditLog: not found surfaces ApprovalRequestNotFoundException")
    void auditLogNotFound() {
        UUID id = UUID.randomUUID();
        when(requestRepository.findById(id)).thenReturn(Mono.empty());
        RetrieveApprovalAuditLogUseCase useCase = new RetrieveApprovalAuditLogUseCase(requestRepository);

        StepVerifier.create(useCase.execute(id)).expectError(ApprovalRequestNotFoundException.class).verify();
    }

    @Test
    @DisplayName("auditLog: collects the ledger into a Mono<List<...>>, preserving order")
    void auditLogSuccess() {
        when(requestRepository.findById(request.getId())).thenReturn(Mono.just(request));
        RetrieveApprovalAuditLogUseCase useCase = new RetrieveApprovalAuditLogUseCase(requestRepository);

        StepVerifier.create(useCase.execute(request.getId()))
                .assertNext(entries -> assertEquals(1, entries.size()))
                .verifyComplete();
    }
}
