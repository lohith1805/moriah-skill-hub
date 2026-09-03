package com.moriah.skillhub.dev;

import com.moriah.skillhub.assessment.QuizAttemptExpiryService;
import com.moriah.skillhub.attendance.AttendanceFinalisationService;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.metrics.StudentMetricsService;
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

    @PostMapping("/{job}/run")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "DEV ONLY — run a scheduled job now. job ∈ "
            + "{attendance-finalisation, metrics-refresh, pip-evaluation, subscription-expiry, quiz-attempt-expiry}. "
            + "For a realistic PIP test, run them in that order.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> run(@PathVariable String job) {
        int processed = switch (job) {
            case "attendance-finalisation" -> attendanceFinalisationService.finalise();
            case "metrics-refresh" -> studentMetricsService.refresh();
            case "pip-evaluation" -> pipEvaluationService.evaluate();
            case "subscription-expiry" -> subscriptionExpiryService.runExpiry();
            case "quiz-attempt-expiry" -> quizAttemptExpiryService.expire();
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Unknown job '" + job + "'. Valid: attendance-finalisation, metrics-refresh, "
                            + "pip-evaluation, subscription-expiry, quiz-attempt-expiry.");
        };
        log.warn("[dev/jobs] '{}' run manually — {} rows processed", job, processed);
        return ResponseEntity.ok(ApiResponse.success(Map.of("job", job, "processed", processed)));
    }
}
