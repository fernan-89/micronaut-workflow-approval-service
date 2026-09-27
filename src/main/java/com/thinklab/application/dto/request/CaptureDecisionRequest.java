package com.thinklab.application.dto.request;

import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

/** DTO for capturing one approver's decision (BIAN Behavior Qualifier: {@code decision/capture}). */
@Serdeable
public record CaptureDecisionRequest(
        @NotNull(message = "Outcome is required")
        DecisionOutcome outcome,

        String comment
) {}
