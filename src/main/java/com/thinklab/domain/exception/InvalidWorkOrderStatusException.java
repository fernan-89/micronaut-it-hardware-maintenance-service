package com.thinklab.domain.exception;

/**
 * Domain Exception: Indicates an illegal lifecycle transition on a
 * {@link com.thinklab.domain.model.WorkOrder} (for example, starting repair on a ticket that has not
 * been scheduled yet, or mutating a CLOSED/CANCELLED terminal ticket).
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict (ADR-019 platform-wide). The request is well formed but
 * collides with the aggregate's current state, the same contract used for every other state conflict
 * on the platform.
 */
public class InvalidWorkOrderStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-WO-00409";

    public InvalidWorkOrderStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
