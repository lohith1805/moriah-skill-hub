package com.moriah.skillhub.common.dto;

import java.time.Instant;

/**
 * The single response envelope every endpoint returns. Never a bare object, never a bare list.
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetail error,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> failure(ErrorDetail error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }
}
