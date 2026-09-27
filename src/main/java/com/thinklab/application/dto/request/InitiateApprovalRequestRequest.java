package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO for ApprovalRequest Creation Request (BIAN Behavior Qualifier: {@code initiate}).
 *
 * <p>{@code subjectType}/{@code subjectId} are opaque to this service - the calling Service Domain's
 * own vocabulary (e.g. {@code "ChangeRequest"}). {@code requesterId} is explicit because the caller
 * is typically another service acting on a human's behalf, not the human calling directly.
 */
@Serdeable
public record InitiateApprovalRequestRequest(
        @NotBlank(message = "Subject Type is required")
        String subjectType,

        @NotNull(message = "Subject ID is required")
        UUID subjectId,

        @NotNull(message = "Requester ID is required")
        UUID requesterId,

        @NotNull(message = "Policy ID is required")
        UUID policyId
) {}
