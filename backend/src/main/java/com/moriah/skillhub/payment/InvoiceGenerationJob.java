package com.moriah.skillhub.payment;

import com.moriah.skillhub.common.job.JobRun;
import com.moriah.skillhub.common.job.JobRunTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * `/architect feature 07` decision: triggered by {@link PaymentCapturedEvent} after the webhook's
 * transaction commits, via Spring's own {@code @TransactionalEventListener} + {@code @Async} —
 * not feature 08's Redis notification queue (that queue is for dispatch fan-out, not
 * cross-feature job triggering; this job's trigger was already decided independently). Matches
 * progress-tracker.md's Nightly Job Chain listing this job's schedule as "async," not a fixed
 * time like the other three. No automatic retry if it fails — acceptable for now; a failed
 * invoice render/upload is diagnosable via its {@code job_runs} row and can be re-triggered
 * manually, and no feature yet reads {@code invoices.pdf_key} on a schedule that would need one.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InvoiceGenerationJob {

    private static final String JOB_NAME = "InvoiceGenerationJob";

    private final InvoiceService invoiceService;
    private final JobRunTracker jobRunTracker;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        JobRun run = jobRunTracker.start(JOB_NAME);
        try {
            invoiceService.renderAndUpload(event.paymentId());
            jobRunTracker.succeed(run.getId(), 1);
        } catch (Exception e) {
            log.error("[{}] failed for payment {}", JOB_NAME, event.paymentId(), e);
            jobRunTracker.fail(run.getId(), e.getMessage());
        }
    }
}
