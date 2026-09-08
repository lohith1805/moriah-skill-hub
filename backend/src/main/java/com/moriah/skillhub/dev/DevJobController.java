package com.moriah.skillhub.dev;

import com.moriah.skillhub.assessment.QuizAttemptExpiryService;
import com.moriah.skillhub.attendance.AttendanceFinalisationService;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.metrics.StudentMetricsService;
import com.moriah.skillhub.payment.InvoiceService;
import com.moriah.skillhub.payment.entity.Invoice;
import com.moriah.skillhub.payment.entity.InvoiceStatus;
import com.moriah.skillhub.payment.repository.InvoiceRepository;
import com.moriah.skillhub.pip.PipEvaluationService;
import com.moriah.skillhub.subscription.SubscriptionExpiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Fire a scheduled job on demand so it can be tested without waiting for its cron (the nightly
 * attendance → metrics → PIP chain runs at 01:30–02:00, subscription expiry at 03:00, etc.).
 *
 * <p><b>Dev profile only</b> — {@code @Profile("dev")}, so this bean does not exist in {@code test}
 * or {@code prod}. ADMIN-gated on top of that. It just calls the same service method the job's
 * {@code @Scheduled} method calls, in the caller's request thread, and returns the row count.
 *
 * <p>See {@code docs/testing-scheduled-jobs.md}.
 */
@RestController
@RequestMapping("/api/v1/dev/jobs")
@RequiredArgsConstructor
@Profile("dev")
@Slf4j
@Tag(name = "Dev")
public class DevJobController {

    private final AttendanceFinalisationService attendanceFinalisationService;
    private final StudentMetricsService studentMetricsService;
    private final PipEvaluationService pipEvaluationService;
    private final SubscriptionExpiryService subscriptionExpiryService;
    private final QuizAttemptExpiryService quizAttemptExpiryService;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;

    @PostMapping("/{job}/run")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "DEV ONLY — run a scheduled job now. job ∈ "
            + "{attendance-finalisation, metrics-refresh, pip-evaluation, subscription-expiry, quiz-attempt-expiry, "
            + "invoice-generation}. For a realistic PIP test, run them in that order. "
            + "invoice-generation needs ?paymentId= and re-renders the PDF + resends the confirmation email "
            + "(the real InvoiceGenerationJob has no auto-retry).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> run(@PathVariable String job,
                                                                @RequestParam(required = false) Long paymentId) {
        int processed = switch (job) {
            case "attendance-finalisation" -> attendanceFinalisationService.finalise();
            case "metrics-refresh" -> studentMetricsService.refresh();
            case "pip-evaluation" -> pipEvaluationService.evaluate() + pipEvaluationService.autoResolveElapsed();
            case "subscription-expiry" -> subscriptionExpiryService.runExpiry();
            case "quiz-attempt-expiry" -> quizAttemptExpiryService.expire();
            case "invoice-generation" -> regenerateInvoice(paymentId);
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Unknown job '" + job + "'. Valid: attendance-finalisation, metrics-refresh, "
                            + "pip-evaluation, subscription-expiry, quiz-attempt-expiry, invoice-generation.");
        };
        log.warn("[dev/jobs] '{}' run manually — {} rows processed", job, processed);
        return ResponseEntity.ok(ApiResponse.success(Map.of("job", job, "processed", processed)));
    }

    /** Reset the captured payment's invoice to PENDING and re-run the render + confirmation-email
     * step. Used to re-drive a payment whose async {@code InvoiceGenerationJob} failed (it has no
     * built-in retry). */
    private int regenerateInvoice(Long paymentId) {
        if (paymentId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "invoice-generation needs a ?paymentId= query parameter.");
        }
        Invoice invoice = invoiceRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVOICE_NOT_FOUND, String.valueOf(paymentId)));
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setPdfKey(null);
        invoice.setIssuedAt(null);
        invoiceRepository.save(invoice);
        invoiceService.renderAndUpload(paymentId);
        return 1;
    }
}
