package com.moriah.skillhub.common.dto;

/**
 * One field-level validation failure inside {@link ErrorDetail#fieldErrors()}.
 * <p>
 * Named {@code ValidationFieldError} rather than {@code FieldError} to avoid colliding with
 * {@link org.springframework.validation.FieldError}, which {@code GlobalExceptionHandler} maps
 * from on every {@code MethodArgumentNotValidException}.
 */
public record ValidationFieldError(String field, String message) {
}
