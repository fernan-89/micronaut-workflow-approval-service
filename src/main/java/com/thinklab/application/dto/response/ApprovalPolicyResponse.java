package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code requiredApprovals}/{@code eligibleApproverIds} describe the first stage (the whole quorum of a one-stage policy); {@code stages} is the chain. */
@Serdeable
public record ApprovalPolicyResponse(
        UUID id,
        UUID organisationId,
        String name,
        int requiredApprovals,
        List<UUID> eligibleApproverIds,
        List<StageResponse> stages,
        Instant createdAt,
        Instant updatedAt
) {}
