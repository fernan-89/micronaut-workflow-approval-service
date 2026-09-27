package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/** DTO for ApprovalPolicy Creation Request (BIAN Behavior Qualifier: {@code policy/initiate}). */
@Serdeable
public record InitiatePolicyRequest(
        @NotBlank(message = "Name is required")
        String name,

        @Positive(message = "requiredApprovals must be positive")
        int requiredApprovals,

        @NotEmpty(message = "At least one eligible approver is required")
        List<UUID> eligibleApproverIds
) {}
