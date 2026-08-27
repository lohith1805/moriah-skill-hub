package com.moriah.skillhub.common.dto;

import com.moriah.skillhub.common.exception.ErrorCode;

import java.util.List;

/**
 * The {@code error} member of {@link ApiResponse} when {@code success} is {@code false}.
 * {@code fieldErrors} is empty except for {@code VALIDATION_FAILED}.
 */
public record ErrorDetail(
        String code,
        String message,
        List<ValidationFieldError> fieldErrors
) {

    public static ErrorDetail of(ErrorCode errorCode) {
        return new ErrorDetail(errorCode.name(), errorCode.message(), List.of());
    }

    public static ErrorDetail of(ErrorCode errorCode, String message) {
        return new ErrorDetail(errorCode.name(), message, List.of());
    }

    public static ErrorDetail of(ErrorCode errorCode, List<ValidationFieldError> fieldErrors) {
        return new ErrorDetail(errorCode.name(), errorCode.message(), fieldErrors);
    }
}
