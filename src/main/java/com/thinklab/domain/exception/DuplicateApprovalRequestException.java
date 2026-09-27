package com.thinklab.domain.exception;

/**
 * Domain Exception: Thrown when an ApprovalRequest is initiated with a serial number that already exists
 * within the same Organisation scope.
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 */
public class DuplicateApprovalRequestException extends BusinessException {

    private static final String ERROR_CODE = "ERR-WFA-00409";

    public DuplicateApprovalRequestException(String message) {
        super(ERROR_CODE, message);
    }
}
