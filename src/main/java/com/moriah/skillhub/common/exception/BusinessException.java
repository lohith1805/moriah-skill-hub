package com.moriah.skillhub.common.exception;

import lombok.Getter;

/**
 * A business rule was violated (maps to 409). Thrown for domain conflicts such as a duplicate
 * sprint number or a batch already at capacity — never for "not found" or "not permitted",
 * which have their own exception types.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
