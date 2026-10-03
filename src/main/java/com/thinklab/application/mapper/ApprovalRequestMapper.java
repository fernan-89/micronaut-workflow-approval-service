package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateApprovalRequestRequest;
import com.thinklab.application.dto.response.ApprovalAuditEntryResponse;
import com.thinklab.application.dto.response.ApprovalRequestResponse;
import com.thinklab.application.dto.response.DecisionResponse;
import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.ApprovalAuditEntry;
import com.thinklab.domain.model.ApprovalRequest.Decision;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Static factory mapper for ApprovalRequest DTOs and the Domain aggregate. */
public final class ApprovalRequestMapper {

    private ApprovalRequestMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static ApprovalRequest toDomain(InitiateApprovalRequestRequest request, UUID sovereignId, UUID organisationId,
                                            ApprovalPolicy policy, String executor) {
        return ApprovalRequest.createNew(sovereignId, organisationId, request.subjectType(), request.subjectId(),
                request.requesterId(), policy.getId(), policy.getStages(), executor);
    }

    public static ApprovalRequestResponse toResponse(ApprovalRequest request) {
        List<DecisionResponse> decisions = request.getDecisions().stream().map(ApprovalRequestMapper::toResponse).collect(Collectors.toList());
        return new ApprovalRequestResponse(request.getId(), request.getOrganisationId(), request.getSubjectType(), request.getSubjectId(),
                request.getRequesterId(), request.getPolicyId(), request.getRequiredApprovals(), request.getEligibleApproverIds(),
                request.getCurrentStage() + 1, ApprovalPolicyMapper.toStageResponses(request.getStages()),
                request.getStatus().name(), decisions, request.getCreatedAt(), request.getUpdatedAt());
    }

    private static DecisionResponse toResponse(Decision decision) {
        return new DecisionResponse(decision.approverId(), decision.outcome().name(), decision.comment(), decision.decidedAt(), decision.stage() + 1);
    }

    public static ApprovalAuditEntryResponse toResponse(ApprovalAuditEntry entry) {
        return new ApprovalAuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(),
                entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
    }
}
