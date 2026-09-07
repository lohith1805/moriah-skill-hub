package com.moriah.skillhub.common.exception;

import lombok.Getter;

/**
 * The requested entity does not exist (maps to 404). Prefer a feature-specific
 * {@code ErrorCode} (e.g. {@code BATCH_NOT_FOUND}) over the generic
 * {@link ErrorCode#RESOURCE_NOT_FOUND} where one is defined.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final ErrorCode errorCode;

    public ResourceNotFoundException(ErrorCode errorCode, Object identifier) {
        super(errorCode.message() + " [id=" + identifier + "]");
        this.errorCode = errorCode;
    }

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }
}
