package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiatePolicyRequest;
import com.thinklab.application.dto.response.ApprovalPolicyResponse;
import com.thinklab.domain.model.ApprovalPolicy;

import java.util.UUID;

/** Static factory mapper for ApprovalPolicy DTOs and the Domain aggregate. */
public final class ApprovalPolicyMapper {

    private ApprovalPolicyMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static ApprovalPolicy toDomain(InitiatePolicyRequest request, UUID sovereignId, UUID organisationId) {
        return ApprovalPolicy.createNew(sovereignId, organisationId, request.name(), request.requiredApprovals(), request.eligibleApproverIds());
    }

    public static ApprovalPolicyResponse toResponse(ApprovalPolicy policy) {
        return new ApprovalPolicyResponse(policy.getId(), policy.getOrganisationId(), policy.getName(),
                policy.getRequiredApprovals(), policy.getEligibleApproverIds(), policy.getCreatedAt(), policy.getUpdatedAt());
    }
}
