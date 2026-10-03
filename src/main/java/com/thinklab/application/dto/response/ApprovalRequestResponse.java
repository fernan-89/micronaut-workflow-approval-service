package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for ApprovalRequest output payload. {@code status} is what a synchronous caller (e.g.
 * {@code it-change-management}'s {@code approval/capture}) reads right after
 * {@code decision/capture} to react immediately - no polling or event needed for the common case.
 *
 * <p>With an approval chain (ADR-033) {@code currentStage} (one-based) is the stage the request is waiting on, {@code stages} is the
 * whole chain, and {@code requiredApprovals}/{@code eligibleApproverIds} describe the CURRENT stage - for a one-stage request that is
 * exactly what they always meant.
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
        int currentStage,
        List<StageResponse> stages,
        String status,
        List<DecisionResponse> decisions,
        Instant createdAt,
        Instant updatedAt
) {}
