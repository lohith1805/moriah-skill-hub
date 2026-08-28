package com.moriah.skillhub.payment;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.payment.entity.Invoice;
import com.moriah.skillhub.payment.entity.InvoiceStatus;
import com.moriah.skillhub.payment.entity.Payment;
import com.moriah.skillhub.payment.repository.InvoiceRepository;
import com.moriah.skillhub.payment.repository.PaymentRepository;
import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import com.moriah.skillhub.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;

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
