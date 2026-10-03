package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * DTO for ApprovalPolicy Update (BIAN Behavior Qualifier: {@code policy/update}). Same shape as the creation request: either
 * {@code stages} or the single-stage pair, never both.
 */
@Serdeable
public record UpdatePolicyRequest(
        @NotBlank(message = "Name is required")
        String name,

        @Positive(message = "requiredApprovals must be positive")
        Integer requiredApprovals,

        List<UUID> eligibleApproverIds,

        @Size(max = 10, message = "A policy can have at most 10 stages")
        List<@Valid StageRequest> stages
) {}
