package com.moriah.skillhub.payment;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.payment.dto.InvoiceResponse;
import com.moriah.skillhub.payment.entity.Invoice;
import com.moriah.skillhub.payment.entity.InvoiceStatus;
import com.moriah.skillhub.payment.entity.Payment;
import com.moriah.skillhub.payment.entity.PaymentStatus;
import com.moriah.skillhub.payment.repository.InvoiceRepository;
import com.moriah.skillhub.payment.repository.PaymentRepository;
import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import com.moriah.skillhub.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Clears build-plan.md feature 07's stub: "InvoiceGenerationJob writes the PDF locally and logs.
 * S3 upload is wired at feature 08." The PDF itself was always genuinely rendered (not a no-op
 * stub) — only the upload destination was deferred. {@code uploadTrusted}, not {@code upload} —
 * this PDF is server-generated from server-computed data, never client input, so there's nothing
 * for {@link StorageService}'s content-type/magic-byte/size validation to protect against.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private static final String KEY_PREFIX = "invoices/";

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    /** Feature 23 hardening: deliberately <b>not</b> {@code @Transactional} any more — {@code
     * storageService.uploadTrusted} is an outbound S3 call, and AGENTS.md is explicit ("never make
     * an outbound HTTP call inside a transaction"). Every repository call below still runs inside
     * its own short transaction via Spring Data's per-call proxy default (same precedent {@code
     * SubmissionVerificationRetryJob.retry()}'s own Javadoc already documents for this exact
     * "reads, then an external call, then a final write" shape) — nothing needs a single shared
     * transaction spanning the whole method: the reads are independent lookups, and {@code
     * invoiceRepository.save(invoice)} at the end persists a plain, by-then-detached Java object
     * (its mutated fields, not a still-open persistence-context flush) exactly like every other
     * caller of a Spring Data {@code save()} on a previously-loaded entity. */
    public void renderAndUpload(Long paymentId) {
        Invoice invoice = invoiceRepository.findByPaymentId(paymentId).orElse(null);
        if (invoice == null) {
            log.error("[invoice] no invoice row for payment {} — nothing to render", paymentId);
            return;
        }
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            log.info("[invoice] invoice {} already in status {}, skipping", invoice.getId(), invoice.getStatus());
            return;
        }

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        String planName = subscriptionPlanRepository.findById(payment.getPlanId())
                .map(SubscriptionPlan::getName)
                .orElse("Subscription");

        byte[] pdfBytes = renderPdf(invoice, payment, planName);
        String key = storageService.uploadTrusted(KEY_PREFIX + invoice.getInvoiceNumber() + ".pdf",
                pdfBytes, "application/pdf");

        invoice.setPdfKey(key);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.now());
        invoiceRepository.save(invoice);

        // Feature 23 hardening: AGENTS.md "AuditLogService wired into every financial ...
        // mutation" — invoice issuance moves this from PENDING to ISSUED and was previously
        // unaudited. No human caller (InvoiceGenerationJob, an @Async AFTER_COMMIT listener), same
        // null-actor precedent PipEvaluationService.fire()/PaymentWebhookService's own capture/
        // refund audit calls already establish for a system-triggered write.
        auditLogService.record(null, "INVOICE_ISSUED", "Invoice", invoice.getId(), InvoiceStatus.PENDING, InvoiceStatus.ISSUED);

        log.info("[invoice] rendered and uploaded {} to {}", invoice.getInvoiceNumber(), key);

        sendConfirmationEmail(invoice, payment, planName, pdfBytes);
    }

    /**
     * The subscription-confirmation email, sent here rather than from the webhook so the freshly
     * rendered invoice PDF can be attached. {@code enqueueNow} (not {@code enqueueAfterCommit}) —
     * this method runs with no ambient transaction, triggered by an already-committed payment.
     * A missing / blank recipient just skips the send (nothing to email).
     */
    private void sendConfirmationEmail(Invoice invoice, Payment payment, String planName, byte[] pdfBytes) {
        String to = payment.getUser().getEmail();
        if (to == null || to.isBlank()) {
            return;
        }
        notificationService.enqueueNow(payment.getUser().getId(), NotificationChannel.EMAIL,
                "SUBSCRIPTION_CONFIRMATION", Map.of(
                        "to", to,
                        "subject", "Payment received — your Moriah Skill Hub subscription is active",
                        "body", "Thanks for your payment. Your " + planName + " plan is now active.\n\n"
                                + "Invoice " + invoice.getInvoiceNumber() + " for "
                                + payment.getCurrency() + " " + invoice.getTotalAmount().toPlainString()
                                + " is attached to this email.\n\n"
                                + "Sign in to see your dashboard — if your plan includes a batch you'll be "
                                + "placed into one automatically and we'll let you know.",
                        "attachmentBase64", Base64.getEncoder().encodeToString(pdfBytes),
                        "attachmentFilename", invoice.getInvoiceNumber() + ".pdf",
                        "attachmentContentType", "application/pdf"));
    }

    private static final Duration PDF_LINK_TTL = Duration.ofMinutes(10);

    /**
     * The caller's billing history — every CAPTURED / REFUNDED payment they made, each paired
     * with its invoice row if the async {@link InvoiceGenerationJob} has produced one yet. A
     * payment with no invoice row (or one still {@code PENDING}) shows as {@code "PROCESSING"}
     * with a null {@code pdfUrl}; once issued, {@code pdfUrl} is a fresh short-lived pre-signed
     * GET. Read-only, no outbound call — {@code presignedGetUrl} signs locally.
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> listForUser(Long userId, String callerUuid) {
        List<Payment> payments = paymentRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(
                userId, List.of(PaymentStatus.CAPTURED, PaymentStatus.REFUNDED));
        if (payments.isEmpty()) {
            return List.of();
        }

        Map<Long, Invoice> invoiceByPaymentId = invoiceRepository
                .findByPaymentIdIn(payments.stream().map(Payment::getId).toList())
                .stream()
                .collect(Collectors.toMap(i -> i.getPayment().getId(), Function.identity()));

        Map<Long, SubscriptionPlan> planById = subscriptionPlanRepository
                .findAllById(payments.stream().map(Payment::getPlanId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(SubscriptionPlan::getId, Function.identity()));

        return payments.stream().map(payment -> {
            Invoice invoice = invoiceByPaymentId.get(payment.getId());
            SubscriptionPlan plan = planById.get(payment.getPlanId());

            String pdfUrl = null;
            if (invoice != null && invoice.getPdfKey() != null) {
                pdfUrl = storageService.presignedGetUrl(callerUuid, invoice.getPdfKey(), PDF_LINK_TTL).toString();
            }

            return new InvoiceResponse(
                    invoice != null ? invoice.getInvoiceNumber() : null,
                    plan != null ? plan.getCode() : null,
                    plan != null ? plan.getName() : "Subscription",
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getStatus(),
                    invoice != null ? invoice.getStatus().name() : "PROCESSING",
                    payment.getCapturedAt(),
                    pdfUrl,
                    payment.getGatewayOrderId());
        }).toList();
    }

    private byte[] renderPdf(Invoice invoice, Payment payment, String planName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Moriah Skill Hub"));
            doc.add(new Paragraph("Invoice " + invoice.getInvoiceNumber()));
            doc.add(new Paragraph("Date: " + LocalDate.now()));
            doc.add(new Paragraph("Plan: " + planName));
            doc.add(new Paragraph("Amount: " + payment.getCurrency() + " " + invoice.getAmount()));
            doc.add(new Paragraph("Tax: " + payment.getCurrency() + " " + invoice.getTaxAmount()));
            doc.add(new Paragraph("Total: " + payment.getCurrency() + " " + invoice.getTotalAmount()));
            doc.add(new Paragraph("Payment reference: " + payment.getGatewayOrderId()));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[invoice] PDF rendering failed for invoice {}", invoice.getInvoiceNumber(), e);
            throw new IllegalStateException("Invoice PDF rendering failed", e);
        }
    }
}
