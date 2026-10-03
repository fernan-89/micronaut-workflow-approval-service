package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * DTO for ApprovalPolicy Creation Request (BIAN Behavior Qualifier: {@code policy/initiate}).
 *
 * <p>Either {@code stages} (an ordered approval chain, ADR-033) or the original pair {@code requiredApprovals} +
 * {@code eligibleApproverIds} (a single stage), never both.
 */
@Serdeable
public record InitiatePolicyRequest(
        @NotBlank(message = "Name is required")
        String name,

        @Positive(message = "requiredApprovals must be positive")
        Integer requiredApprovals,

        List<UUID> eligibleApproverIds,

        @Size(max = 10, message = "A policy can have at most 10 stages")
        List<@Valid StageRequest> stages
) {}
