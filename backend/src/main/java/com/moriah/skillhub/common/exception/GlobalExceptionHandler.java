package com.moriah.skillhub.common.exception;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.ErrorDetail;
import com.moriah.skillhub.common.dto.ValidationFieldError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * The only place an exception becomes an HTTP response. Every response body here is an
 * {@link ApiResponse} — no exception is allowed to reach the client as a bare stack trace,
 * a SQL fragment, or Spring's default whitelabel error page.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("[error/{}] {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getErrorCode().status())
                .body(ApiResponse.failure(ErrorDetail.of(ex.getErrorCode())));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenOperationException ex) {
        log.warn("[error/{}] {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getErrorCode().status())
                .body(ApiResponse.failure(ErrorDetail.of(ex.getErrorCode())));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("[error/{}] {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getErrorCode().status())
                .body(ApiResponse.failure(ErrorDetail.of(ex.getErrorCode(), ex.getMessage())));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("[error/INSUFFICIENT_ROLE] {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.INSUFFICIENT_ROLE.status())
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.INSUFFICIENT_ROLE)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationFieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ValidationFieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.VALIDATION_FAILED, fields)));
    }

    /**
     * Thrown by Spring's resource-handling machinery (not our routing) for any request that
     * matches no {@code @RequestMapping} and no static resource — e.g. an authenticated request
     * to a path that simply does not exist yet. Without this handler it falls through to {@link
     * #handleUnexpected}, turning a routine 404 into a false-alarm 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("[error/{}] {}", ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.status())
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    /**
     * Thrown by Spring's multipart resolver itself, before {@code ResumeService.upload} ever
     * runs — {@code spring.servlet.multipart.max-file-size} rejects an oversized part earlier
     * than {@code StorageService.upload}'s own {@code Constants.MAX_UPLOAD_BYTES} check can.
     * Same {@code ErrorCode} either way; the client sees one consistent "file too large" error
     * regardless of which layer caught it.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("[error/{}] {}", ErrorCode.FILE_TOO_LARGE, ex.getMessage());
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.status())
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.FILE_TOO_LARGE)));
    }

    /**
     * A webhook request missing its required signature header (e.g. {@code X-Hub-Signature-256},
     * {@code X-Razorpay-Signature}) hits this before any controller body runs — without it, Spring
     * throws before {@code @RequestHeader}'s value is ever compared, and it would otherwise fall
     * through to {@link #handleUnexpected}, turning a routine "no signature presented" into a
     * false-alarm 500 instead of the same {@code INVALID_WEBHOOK_SIGNATURE} a present-but-wrong
     * signature already returns.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("[error/{}] missing required header: {}", ErrorCode.INVALID_WEBHOOK_SIGNATURE, ex.getHeaderName());
        return ResponseEntity.status(ErrorCode.INVALID_WEBHOOK_SIGNATURE.status())
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.INVALID_WEBHOOK_SIGNATURE)));
    }

    /**
     * The last-resort backstop for a "check exists, then save" race that a service's own
     * pre-check couldn't catch (two concurrent requests both pass the exists() check before
     * either commits, so the loser hits the database's own unique constraint instead). Without
     * this handler that race falls through to {@link #handleUnexpected}, turning a documented
     * business-rule 409 (e.g. "a month cannot be generated twice") into a false-alarm 500 under
     * concurrency. Services should still prefer their own explicit pre-check for the common case
     * — this only covers the narrow window a pre-check can't close.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("[error/{}] unique constraint violated: {}", ErrorCode.BUSINESS_RULE_VIOLATION, ex.getMessage());
        return ResponseEntity.status(ErrorCode.BUSINESS_RULE_VIOLATION.status())
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.BUSINESS_RULE_VIOLATION)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("[error/VALIDATION_FAILED] malformed request body: {}", ex.getMessage());
        // A DTO record's compact constructor that throws IllegalArgumentException for a
        // cross-field rule (e.g. CreateSprintRequest's "a sprint must run 1-2 weeks") reaches
        // here wrapped by Jackson, not as a bean-validation error — so its message would
        // otherwise be lost behind the generic text. Surface that deliberate message; keep the
        // generic answer for genuine parse failures (bad JSON, wrong types), whose Jackson
        // messages leak field internals.
        String message = "The request body is malformed or unreadable.";
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null) {
                message = cause.getMessage();
                break;
            }
        }
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.VALIDATION_FAILED, message)));
    }

    /**
     * A required {@code @RequestParam} the caller left off (e.g. {@code GET /api/v1/standups}
     * with no {@code batchId}), or one whose value won't bind to the target type ({@code
     * ?batchId=abc} for a {@code Long}). Both are client mistakes — without this they fall
     * through to {@link #handleUnexpected} and surface as a misleading 500 instead of a 400 that
     * tells the caller which parameter is wrong.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequestParam(Exception ex) {
        String param = ex instanceof MissingServletRequestParameterException missing
                ? missing.getParameterName()
                : ((MethodArgumentTypeMismatchException) ex).getName();
        String message = ex instanceof MissingServletRequestParameterException
                ? "Required request parameter '" + param + "' is missing."
                : "Request parameter '" + param + "' has an invalid value.";
        log.warn("[error/VALIDATION_FAILED] {}", message);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.VALIDATION_FAILED, message)));
    }

    /**
     * The path exists but not for this verb (e.g. {@code GET /api/v1/admin/plans}, which is
     * {@code POST}/{@code PUT}/{@code DELETE}-only). Spring's default surfaces this as a 500
     * through {@link #handleUnexpected}; a 405 is the correct, non-alarming answer.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("[error/METHOD_NOT_ALLOWED] {} not supported for this endpoint", ex.getMethod());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.status())
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.METHOD_NOT_ALLOWED)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("[error/UNEXPECTED] {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.INTERNAL_ERROR)));
    }
}
