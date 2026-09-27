package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Serdeable
public record ApprovalPolicyResponse(
        UUID id,
        UUID organisationId,
        String name,
        int requiredApprovals,
        List<UUID> eligibleApproverIds,
        Instant createdAt,
        Instant updatedAt
) {}
