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
        when(policyRepository.findById(policy.getId(), organisationId)).thenReturn(Mono.empty());
        InitiateApprovalRequestUseCase useCase = new InitiateApprovalRequestUseCase(hashServicePort, policyRepository, requestRepository, true);

        StepVerifier.create(useCase.execute(organisationId, body, EXECUTOR)).expectError(ApprovalPolicyNotFoundException.class).verify();
    }

    @Test
    @DisplayName("initiate: with the tenant scoping off (the default) the policy is found by id alone, as before")
    void initiateUnscopedPolicy() {
        InitiateApprovalRequestRequest body = new InitiateApprovalRequestRequest("ChangeRequest", UUID.randomUUID(), UUID.randomUUID(), policy.getId());
        when(policyRepository.findById(policy.getId())).thenReturn(Mono.just(policy));
        when(hashServicePort.generateSovereignId("approval-request-creation")).thenReturn(Mono.just(request.getId()));
        when(requestRepository.create(any(ApprovalRequest.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        InitiateApprovalRequestUseCase useCase = new InitiateApprovalRequestUseCase(hashServicePort, policyRepository, requestRepository, false);

        StepVerifier.create(useCase.execute(UUID.randomUUID(), body, EXECUTOR)).expectNextCount(1).verifyComplete();

        org.mockito.Mockito.verify(policyRepository, org.mockito.Mockito.never()).findById(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("initiate: a policy of another tenant is the same as none, and nothing is filed")
    void initiateAnotherTenantsPolicy() {
        UUID other = UUID.randomUUID();
        InitiateApprovalRequestRequest body = new InitiateApprovalRequestRequest("ChangeRequest", UUID.randomUUID(), UUID.randomUUID(), policy.getId());
        when(policyRepository.findById(policy.getId(), other)).thenReturn(Mono.empty());
        InitiateApprovalRequestUseCase useCase = new InitiateApprovalRequestUseCase(hashServicePort, policyRepository, requestRepository, true);

        StepVerifier.create(useCase.execute(other, body, EXECUTOR)).expectError(ApprovalPolicyNotFoundException.class).verify();

        org.mockito.Mockito.verify(requestRepository, org.mockito.Mockito.never()).create(any(ApprovalRequest.class));
    }

    @Test
    @DisplayName("initiate: loads the policy, snapshots its quorum, fetches a sovereign id and persists the request")
    void initiateSuccess() {
        InitiateApprovalRequestRequest body = new InitiateApprovalRequestRequest("ChangeRequest", UUID.randomUUID(), UUID.randomUUID(), policy.getId());
        when(policyRepository.findById(policy.getId(), organisationId)).thenReturn(Mono.just(policy));
        when(hashServicePort.generateSovereignId("approval-request-creation")).thenReturn(Mono.just(request.getId()));
        when(requestRepository.create(any(ApprovalRequest.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        InitiateApprovalRequestUseCase useCase = new InitiateApprovalRequestUseCase(hashServicePort, policyRepository, requestRepository, true);

        StepVerifier.create(useCase.execute(organisationId, body, EXECUTOR))
                .assertNext(response -> assertEquals(policy.getRequiredApprovals(), response.requiredApprovals()))
                .verifyComplete();
    }

    // --- RetrieveApprovalRequestUseCase ---

    @Test
    @DisplayName("retrieveById: not found surfaces ApprovalRequestNotFoundException")
    void retrieveByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(requestRepository.findById(id, organisationId)).thenReturn(Mono.empty());
        RetrieveApprovalRequestUseCase useCase = new RetrieveApprovalRequestUseCase(requestRepository);

        StepVerifier.create(useCase.execute(id, organisationId)).expectError(ApprovalRequestNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieveById: maps the found request to a response")
    void retrieveByIdFound() {
        when(requestRepository.findById(request.getId(), organisationId)).thenReturn(Mono.just(request));
        RetrieveApprovalRequestUseCase useCase = new RetrieveApprovalRequestUseCase(requestRepository);

        StepVerifier.create(useCase.execute(request.getId(), organisationId)).expectNextCount(1).verifyComplete();
    }

    // --- RetrieveApprovalRequestsUseCase ---

    @Test
    @DisplayName("retrieveAll: delegates to the repository's tenant-scoped, optionally filtered query")
    void retrieveAll() {
        when(requestRepository.findAllByOrganisationId(organisationId, "ChangeRequest", request.getSubjectId(), ApprovalStatus.PENDING))
                .thenReturn(Flux.just(request));
        RetrieveApprovalRequestsUseCase useCase = new RetrieveApprovalRequestsUseCase(requestRepository);

        StepVerifier.create(useCase.execute(organisationId, "ChangeRequest", request.getSubjectId(), ApprovalStatus.PENDING, null))
                .expectNextCount(1).verifyComplete();
    }

    // --- CaptureDecisionUseCase ---

    @Test
    @DisplayName("captureDecision: not found surfaces ApprovalRequestNotFoundException")
    void captureDecisionNotFound() {
        UUID id = UUID.randomUUID();
        when(requestRepository.findById(id, organisationId)).thenReturn(Mono.empty());
        CaptureDecisionUseCase useCase = new CaptureDecisionUseCase(requestRepository);

        StepVerifier.create(useCase.execute(id, organisationId, approverA, new CaptureDecisionRequest(DecisionOutcome.APPROVE, "ok"), EXECUTOR))
                .expectError(ApprovalRequestNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("captureDecision: applies the domain decision, persists it and returns the resolved response")
    void captureDecisionSuccess() {
        when(requestRepository.findById(request.getId(), organisationId)).thenReturn(Mono.just(request));
        lenient().when(requestRepository.addDecision(any(), any(), any())).thenReturn(Mono.empty());
        CaptureDecisionUseCase useCase = new CaptureDecisionUseCase(requestRepository);

        StepVerifier.create(useCase.execute(request.getId(), organisationId, approverA, new CaptureDecisionRequest(DecisionOutcome.APPROVE, "ok"), EXECUTOR))
                .assertNext(response -> assertEquals("APPROVED", response.status()))
                .verifyComplete();
    }

    // --- CancelApprovalRequestUseCase ---

    @Test
    @DisplayName("cancel: not found surfaces ApprovalRequestNotFoundException")
    void cancelNotFound() {
        UUID id = UUID.randomUUID();
        when(requestRepository.findById(id, organisationId)).thenReturn(Mono.empty());
        CancelApprovalRequestUseCase useCase = new CancelApprovalRequestUseCase(requestRepository);

        StepVerifier.create(useCase.execute(id, organisationId, EXECUTOR)).expectError(ApprovalRequestNotFoundException.class).verify();
    }

    @Test
    @DisplayName("cancel: applies the domain transition and persists the granular status update")
    void cancelSuccess() {
        when(requestRepository.findById(request.getId(), organisationId)).thenReturn(Mono.just(request));
        when(requestRepository.updateStatus(any(), any(), any())).thenReturn(Mono.empty());
        CancelApprovalRequestUseCase useCase = new CancelApprovalRequestUseCase(requestRepository);

        StepVerifier.create(useCase.execute(request.getId(), organisationId, EXECUTOR)).verifyComplete();
    }

    // --- RetrieveApprovalAuditLogUseCase ---

    @Test
    @DisplayName("auditLog: not found surfaces ApprovalRequestNotFoundException")
    void auditLogNotFound() {
        UUID id = UUID.randomUUID();
        when(requestRepository.findById(id, organisationId)).thenReturn(Mono.empty());
        RetrieveApprovalAuditLogUseCase useCase = new RetrieveApprovalAuditLogUseCase(requestRepository);

        StepVerifier.create(useCase.execute(id, organisationId)).expectError(ApprovalRequestNotFoundException.class).verify();
    }

    @Test
    @DisplayName("auditLog: collects the ledger into a Mono<List<...>>, preserving order")
    void auditLogSuccess() {
        when(requestRepository.findById(request.getId(), organisationId)).thenReturn(Mono.just(request));
        RetrieveApprovalAuditLogUseCase useCase = new RetrieveApprovalAuditLogUseCase(requestRepository);

        StepVerifier.create(useCase.execute(request.getId(), organisationId))
                .assertNext(entries -> assertEquals(1, entries.size()))
                .verifyComplete();
    }

    @Test
    @DisplayName("retrieveAll with pendingFor is that approver's inbox, oldest first, and ignores the other filters")
    void retrieveInbox() throws Exception {
        var older = ApprovalRequest.createNew(UUID.randomUUID(), organisationId, "ChangeRequest", UUID.randomUUID(), UUID.randomUUID(), policy.getId(), 1, List.of(approverA), EXECUTOR);
        Thread.sleep(5);
        var newer = ApprovalRequest.createNew(UUID.randomUUID(), organisationId, "ChangeRequest", UUID.randomUUID(), UUID.randomUUID(), policy.getId(), 1, List.of(approverA), EXECUTOR);
        when(requestRepository.findPendingFor(organisationId, approverA)).thenReturn(Flux.just(newer, older));
        RetrieveApprovalRequestsUseCase useCase = new RetrieveApprovalRequestsUseCase(requestRepository);

        StepVerifier.create(useCase.execute(organisationId, "Ignored", UUID.randomUUID(), ApprovalStatus.APPROVED, approverA))
                .assertNext(response -> assertEquals(older.getId(), response.id()))
                .assertNext(response -> assertEquals(newer.getId(), response.id()))
                .verifyComplete();
    }

    @Test
    @DisplayName("every by-id route looks the request up in the caller's tenant: another tenant's request is the same as none, and nothing is read, decided or cancelled")
    void anotherTenantsRequest() {
        UUID other = UUID.randomUUID();
        when(requestRepository.findById(request.getId(), other)).thenReturn(Mono.empty());
        CaptureDecisionUseCase capture = new CaptureDecisionUseCase(requestRepository);

        StepVerifier.create(new RetrieveApprovalRequestUseCase(requestRepository).execute(request.getId(), other)).expectError(ApprovalRequestNotFoundException.class).verify();
        StepVerifier.create(new RetrieveApprovalAuditLogUseCase(requestRepository).execute(request.getId(), other)).expectError(ApprovalRequestNotFoundException.class).verify();
        StepVerifier.create(capture.execute(request.getId(), other, approverA, new CaptureDecisionRequest(DecisionOutcome.APPROVE, "ok"), EXECUTOR)).expectError(ApprovalRequestNotFoundException.class).verify();
        StepVerifier.create(new CancelApprovalRequestUseCase(requestRepository).execute(request.getId(), other, EXECUTOR)).expectError(ApprovalRequestNotFoundException.class).verify();

        org.mockito.Mockito.verify(requestRepository, org.mockito.Mockito.never()).addDecision(any(), any(), any());
        org.mockito.Mockito.verify(requestRepository, org.mockito.Mockito.never()).updateStatus(any(), any(), any());
    }
}
