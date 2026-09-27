package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.CaptureDecisionRequest;
import com.thinklab.application.dto.request.InitiateApprovalRequestRequest;
import com.thinklab.application.dto.request.InitiatePolicyRequest;
import com.thinklab.application.dto.request.UpdatePolicyRequest;
import com.thinklab.application.dto.response.ApprovalAuditEntryResponse;
import com.thinklab.application.dto.response.ApprovalPolicyResponse;
import com.thinklab.application.dto.response.ApprovalRequestResponse;
import com.thinklab.application.usecase.CancelApprovalRequestUseCase;
import com.thinklab.application.usecase.CaptureDecisionUseCase;
import com.thinklab.application.usecase.InitiateApprovalRequestUseCase;
import com.thinklab.application.usecase.InitiatePolicyUseCase;
import com.thinklab.application.usecase.RetrieveApprovalAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveApprovalRequestUseCase;
import com.thinklab.application.usecase.RetrieveApprovalRequestsUseCase;
import com.thinklab.application.usecase.RetrievePoliciesUseCase;
import com.thinklab.application.usecase.RetrievePolicyUseCase;
import com.thinklab.application.usecase.UpdatePolicyUseCase;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowApprovalControllerTest {

    @Mock private InitiatePolicyUseCase initiatePolicyUseCase;
    @Mock private RetrievePolicyUseCase retrievePolicyUseCase;
    @Mock private RetrievePoliciesUseCase retrievePoliciesUseCase;
    @Mock private UpdatePolicyUseCase updatePolicyUseCase;
    @Mock private InitiateApprovalRequestUseCase initiateApprovalRequestUseCase;
    @Mock private RetrieveApprovalRequestUseCase retrieveApprovalRequestUseCase;
    @Mock private RetrieveApprovalRequestsUseCase retrieveApprovalRequestsUseCase;
    @Mock private CaptureDecisionUseCase captureDecisionUseCase;
    @Mock private CancelApprovalRequestUseCase cancelApprovalRequestUseCase;
    @Mock private RetrieveApprovalAuditLogUseCase retrieveApprovalAuditLogUseCase;

    private WorkflowApprovalController controller;
    private UUID id;
    private static final String TENANT = UUID.randomUUID().toString();
    private static final String EXECUTOR = "op-1";

    @BeforeEach
    void setUp() {
        controller = new WorkflowApprovalController(initiatePolicyUseCase, retrievePolicyUseCase, retrievePoliciesUseCase,
                updatePolicyUseCase, initiateApprovalRequestUseCase, retrieveApprovalRequestUseCase, retrieveApprovalRequestsUseCase,
                captureDecisionUseCase, cancelApprovalRequestUseCase, retrieveApprovalAuditLogUseCase);
        id = UUID.randomUUID();
    }

    private ApprovalPolicyResponse samplePolicyResponse() {
        return new ApprovalPolicyResponse(id, UUID.randomUUID(), "CAB", 1, List.of(UUID.randomUUID()), Instant.now(), Instant.now());
    }

    private ApprovalRequestResponse sampleRequestResponse() {
        return new ApprovalRequestResponse(id, UUID.randomUUID(), "ChangeRequest", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, List.of(UUID.randomUUID()), "PENDING", List.of(), Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("policy/initiate returns 201 Created")
    void initiatePolicy() {
        InitiatePolicyRequest request = new InitiatePolicyRequest("CAB", 1, List.of(UUID.randomUUID()));
        when(initiatePolicyUseCase.execute(any(), eq(request))).thenReturn(Mono.just(samplePolicyResponse()));

        var response = controller.initiatePolicy(TENANT, request).block();
        assertEquals(HttpStatus.CREATED, response.getStatus());
    }

    @Test
    @DisplayName("policy/{id}/retrieve returns 200 OK")
    void retrievePolicyById() {
        when(retrievePolicyUseCase.execute(id)).thenReturn(Mono.just(samplePolicyResponse()));

        var response = controller.retrievePolicyById(id).block();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @DisplayName("policy/retrieve delegates with the optional name filter")
    void retrievePolicies() {
        when(retrievePoliciesUseCase.execute(any(), eq("CAB"))).thenReturn(Flux.just(samplePolicyResponse()));

        var result = controller.retrievePolicies(TENANT, "CAB").block();
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("policy/{id}/update returns 204 No Content")
    void updatePolicy() {
        UpdatePolicyRequest request = new UpdatePolicyRequest("CAB-v2", 2, List.of(UUID.randomUUID()));
        when(updatePolicyUseCase.execute(id, request)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.updatePolicy(id, request).block().getStatus());
    }

    @Test
    @DisplayName("initiate returns 201 Created")
    void initiate() {
        InitiateApprovalRequestRequest request = new InitiateApprovalRequestRequest("ChangeRequest", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(initiateApprovalRequestUseCase.execute(any(), eq(request), eq(EXECUTOR))).thenReturn(Mono.just(sampleRequestResponse()));

        var response = controller.initiate(TENANT, EXECUTOR, request).block();
        assertEquals(HttpStatus.CREATED, response.getStatus());
    }

    @Test
    @DisplayName("{id}/retrieve returns 200 OK")
    void retrieveById() {
        when(retrieveApprovalRequestUseCase.execute(id)).thenReturn(Mono.just(sampleRequestResponse()));

        var response = controller.retrieveById(id).block();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @DisplayName("retrieve delegates with the optional subjectType/subjectId/status filters")
    void retrieveAll() {
        when(retrieveApprovalRequestsUseCase.execute(any(), any(), any(), any())).thenReturn(Flux.just(sampleRequestResponse()));

        var result = controller.retrieveAll(TENANT, null, null, null).block();
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("{id}/decision/capture returns 200 OK with the post-decision state")
    void captureDecision() {
        String approver = UUID.randomUUID().toString();
        CaptureDecisionRequest request = new CaptureDecisionRequest(DecisionOutcome.APPROVE, "ok");
        when(captureDecisionUseCase.execute(eq(id), any(), eq(request), eq(approver))).thenReturn(Mono.just(sampleRequestResponse()));

        var response = controller.captureDecision(id, approver, request).block();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @DisplayName("{id}/control/cancel returns 204 No Content")
    void controlCancel() {
        when(cancelApprovalRequestUseCase.execute(id, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlCancel(id, EXECUTOR).block().getStatus());
    }

    @Test
    @DisplayName("{id}/audit-log/retrieve delegates to RetrieveApprovalAuditLogUseCase")
    void retrieveAuditLog() {
        ApprovalAuditEntryResponse entry = new ApprovalAuditEntryResponse(Instant.now(), "INITIATED", EXECUTOR, null, "PENDING", "d");
        when(retrieveApprovalAuditLogUseCase.execute(id)).thenReturn(Mono.just(List.of(entry)));

        var result = controller.retrieveAuditLog(id).block();
        assertEquals(1, result.size());
    }
}
