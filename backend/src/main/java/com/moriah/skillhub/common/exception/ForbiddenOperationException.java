package com.moriah.skillhub.common.exception;

import lombok.Getter;

/**
 * The caller is authenticated but not permitted to perform this action (maps to 403) — an
 * ownership check or an entitlement check failed. {@code @PreAuthorize} handles the role check;
 * this exception is for the ownership/entitlement checks a service method makes afterward.
 */
@Getter
public class ForbiddenOperationException extends RuntimeException {

    private final ErrorCode errorCode;

    public ForbiddenOperationException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }
}
