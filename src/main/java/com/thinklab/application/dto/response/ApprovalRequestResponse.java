package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for ApprovalRequest output payload. {@code status} is what a synchronous caller (e.g.
 * {@code it-change-management}'s {@code approval/capture}) reads right after
 * {@code decision/capture} to react immediately - no polling or event needed for the common case.
 */
@Serdeable
public record ApprovalRequestResponse(
        UUID id,
        UUID organisationId,
        String subjectType,
        UUID subjectId,
        UUID requesterId,
        UUID policyId,
        int requiredApprovals,
        List<UUID> eligibleApproverIds,
        String status,
        List<DecisionResponse> decisions,
        Instant createdAt,
        Instant updatedAt
) {}
