package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiatePolicyRequest;
import com.thinklab.application.dto.request.StageRequest;
import com.thinklab.application.dto.response.ApprovalPolicyResponse;
import com.thinklab.application.dto.response.StageResponse;
import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.domain.model.ApprovalStage;

import java.util.List;
import java.util.UUID;

/** Static factory mapper for ApprovalPolicy DTOs and the Domain aggregate. */
public final class ApprovalPolicyMapper {

    private ApprovalPolicyMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static ApprovalPolicy toDomain(InitiatePolicyRequest request, UUID sovereignId, UUID organisationId) {
        return ApprovalPolicy.createNew(sovereignId, organisationId, request.name(),
                toStages(request.requiredApprovals(), request.eligibleApproverIds(), request.stages()));
    }

    /**
     * The chain a request describes: its {@code stages}, or - the original shape - one stage from {@code requiredApprovals} +
     * {@code eligibleApproverIds}. Giving both is ambiguous and refused, and so is giving neither.
     */
    public static List<ApprovalStage> toStages(Integer requiredApprovals, List<UUID> eligibleApproverIds, List<StageRequest> stages) {
        boolean hasStages = stages != null && !stages.isEmpty();
        boolean hasQuorum = requiredApprovals != null || eligibleApproverIds != null;
        if (hasStages && hasQuorum) {
            throw new IllegalArgumentException("Give either stages, or requiredApprovals with eligibleApproverIds - not both.");
        }
        if (hasStages) {
            return stages.stream().map(stage -> new ApprovalStage(stage.requiredApprovals(), stage.eligibleApproverIds())).toList();
        }
        if (requiredApprovals == null || eligibleApproverIds == null) {
            throw new IllegalArgumentException("Either stages, or requiredApprovals with eligibleApproverIds, is required.");
        }
        return List.of(new ApprovalStage(requiredApprovals, eligibleApproverIds));
    }

    public static ApprovalPolicyResponse toResponse(ApprovalPolicy policy) {
        return new ApprovalPolicyResponse(policy.getId(), policy.getOrganisationId(), policy.getName(),
                policy.getRequiredApprovals(), policy.getEligibleApproverIds(), toStageResponses(policy.getStages()),
                policy.getCreatedAt(), policy.getUpdatedAt());
    }

    static List<StageResponse> toStageResponses(List<ApprovalStage> stages) {
        return stages.stream().map(stage -> new StageResponse(stage.requiredApprovals(), stage.eligibleApproverIds())).toList();
    }
}
