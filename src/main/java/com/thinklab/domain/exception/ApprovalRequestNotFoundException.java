package com.thinklab.domain.exception;

import java.util.Objects;
import java.util.UUID;

/**
 * Domain Exception: Indicates that a requested {@link com.thinklab.domain.model.ApprovalRequest} could not
 * be resolved from the repository.
 *
 * <p>RFC 7807 mapping: HTTP 404 Not Found.
 */
public class ApprovalRequestNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "ERR-WFA-00404";

    public ApprovalRequestNotFoundException(UUID id) {
        super(
                ERROR_CODE,
                String.format("ApprovalRequest with sovereign ID [%s] could not be found in the system of record.",
                        Objects.requireNonNull(id, "Domain Exception constraint violated: UUID cannot be null."))
        );
    }

    public ApprovalRequestNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
