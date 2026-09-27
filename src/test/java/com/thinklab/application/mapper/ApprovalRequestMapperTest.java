package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateApprovalRequestRequest;
import com.thinklab.application.dto.response.ApprovalRequestResponse;
import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalRequestMapperTest {

    private static final String EXECUTOR = "op-1";

    @Test
    @DisplayName("toDomain snapshots the policy's quorum and approvers onto the new request")
    void toDomain() {
        UUID approverA = UUID.randomUUID();
        ApprovalPolicy policy = ApprovalPolicy.createNew(UUID.randomUUID(), UUID.randomUUID(), "CAB", 1, List.of(approverA));
        InitiateApprovalRequestRequest request = new InitiateApprovalRequestRequest("ChangeRequest", UUID.randomUUID(), UUID.randomUUID(), policy.getId());
        UUID id = UUID.randomUUID();
        UUID organisationId = UUID.randomUUID();

        ApprovalRequest approvalRequest = ApprovalRequestMapper.toDomain(request, id, organisationId, policy, EXECUTOR);

        assertEquals(id, approvalRequest.getId());
        assertEquals(organisationId, approvalRequest.getOrganisationId());
        assertEquals(policy.getId(), approvalRequest.getPolicyId());
        assertEquals(policy.getRequiredApprovals(), approvalRequest.getRequiredApprovals());
        assertEquals(policy.getEligibleApproverIds(), approvalRequest.getEligibleApproverIds());
    }

    @Test
    @DisplayName("toResponse projects the request, its decisions and its audit entries, including a null fromStatus")
    void toResponse() {
        UUID approverA = UUID.randomUUID();
        ApprovalRequest request = ApprovalRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), "ChangeRequest",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, List.of(approverA), EXECUTOR);
        request.captureDecision(approverA, DecisionOutcome.APPROVE, "ok", EXECUTOR);

        ApprovalRequestResponse response = ApprovalRequestMapper.toResponse(request);

        assertEquals(request.getId(), response.id());
        assertEquals("APPROVED", response.status());
        assertEquals(1, response.decisions().size());
        assertEquals(approverA, response.decisions().get(0).approverId());
        assertEquals("APPROVE", response.decisions().get(0).outcome());

        var initiatedEntry = ApprovalRequestMapper.toResponse(request.getAuditTrail().get(0));
        assertEquals(null, initiatedEntry.fromStatus());
        assertEquals("PENDING", initiatedEntry.toStatus());

        var decisionEntry = ApprovalRequestMapper.toResponse(request.getAuditTrail().get(1));
        assertEquals("PENDING", decisionEntry.fromStatus());
        assertEquals("APPROVED", decisionEntry.toStatus());
    }

    @Test
    @DisplayName("the mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        var constructor = ApprovalRequestMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        assertTrue(ex.getCause() instanceof UnsupportedOperationException);
    }
}
