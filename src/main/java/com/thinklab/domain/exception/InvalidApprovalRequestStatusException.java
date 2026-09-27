package com.thinklab.domain.exception;

/**
 * Domain Exception: Indicates an illegal lifecycle transition or a decision-capture rule violation on
 * an {@link com.thinklab.domain.model.ApprovalRequest} (an illegal status for the action attempted, an
 * approver outside the policy's eligible list, or an approver who already decided).
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict (ADR-019 platform-wide). The request is well formed but
 * collides with the aggregate's current state, the same contract used for every other state conflict
 * on the platform.
 */
public class InvalidApprovalRequestStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-WFA-00409";

    public InvalidApprovalRequestStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
