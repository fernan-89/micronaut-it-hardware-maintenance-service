package com.thinklab.domain.exception;

/**
 * Domain Exception: Thrown when an WorkOrder is initiated with a serial number that already exists
 * within the same Organisation scope.
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 */
public class DuplicateWorkOrderException extends BusinessException {

    private static final String ERROR_CODE = "ERR-WO-00409";

    public DuplicateWorkOrderException(String message) {
        super(ERROR_CODE, message);
    }
}
