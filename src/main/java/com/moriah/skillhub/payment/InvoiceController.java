package com.moriah.skillhub.payment;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.common.security.CurrentUserUuid;
import com.moriah.skillhub.payment.dto.InvoiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The caller's own billing history. Mapped under {@code /api/v1/subscriptions} — like
 * {@link CheckoutController}, this is a {@code payment/}-package controller that serves the
 * "Subscription &amp; Billing" surface, so {@code subscription/} never has to depend on
 * {@code payment/}.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/me/invoices")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "The caller's billing history — captured/refunded payments with invoice status "
            + "and a short-lived PDF link (null while the invoice is still being generated)")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> myInvoices(
            @CurrentUser Long callerUserId, @CurrentUserUuid String callerUuid) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.listForUser(callerUserId, callerUuid)));
    }
}
