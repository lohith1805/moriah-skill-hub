package com.moriah.skillhub.admin;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.common.security.CurrentUserUuid;
import com.moriah.skillhub.payment.AdminPaymentService;
import com.moriah.skillhub.payment.dto.AdminPaymentResponse;
import com.moriah.skillhub.payment.dto.PaymentSummaryResponse;
import com.moriah.skillhub.payment.dto.RefundPaymentRequest;
import com.moriah.skillhub.payment.entity.PaymentGateway;
import com.moriah.skillhub.payment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin transactions &amp; refunds (gap B1.11). {@code GET /admin/metrics/revenue} is aggregate
 * only; this is the row-level list the FE refund UI needs. Every method is {@code ADMIN}-only.
 * The path identifier is {@code gatewayOrderId} — {@code payments} has no {@code uuid} and
 * {@code id} never leaves the service layer. Routes through {@code payment/AdminPaymentService}
 * the same cross-module way {@code AdminPlanController} calls {@code EntitlementService}.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List payments, newest first — optional status / gateway / userUuid filters")
    public ResponseEntity<ApiResponse<PageResponse<AdminPaymentResponse>>> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentGateway gateway,
            @RequestParam(required = false) String userUuid,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                adminPaymentService.list(status, gateway, userUuid, pageable)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Payment counts and amount totals per status, plus total captured / refunded")
    public ResponseEntity<ApiResponse<PaymentSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(adminPaymentService.summary()));
    }

    @GetMapping("/{gatewayOrderId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "One payment's detail")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment detail"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No payment with this gateway order id")
    })
    public ResponseEntity<ApiResponse<AdminPaymentResponse>> get(@PathVariable String gatewayOrderId) {
        return ResponseEntity.ok(ApiResponse.success(adminPaymentService.get(gatewayOrderId)));
    }

    @GetMapping("/{gatewayOrderId}/invoice")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "A short-lived pre-signed URL for this payment's invoice PDF")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "{ url }"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No payment, or its invoice PDF is not ready yet")
    })
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> invoice(
            @PathVariable String gatewayOrderId, @CurrentUserUuid String callerUuid) {
        return ResponseEntity.ok(ApiResponse.success(
                java.util.Map.of("url", adminPaymentService.invoicePdfUrl(gatewayOrderId, callerUuid))));
    }

    @PostMapping("/{gatewayOrderId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Issue a full refund through the original gateway — only for a CAPTURED payment, once. "
            + "The gateway's refund webhook reconciles the final state.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Refund requested; payment marked REFUNDED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No payment with this gateway order id"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Payment is not CAPTURED, or already refunded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "The payment gateway rejected the refund")
    })
    public ResponseEntity<ApiResponse<AdminPaymentResponse>> refund(
            @PathVariable String gatewayOrderId,
            @Valid @RequestBody(required = false) RefundPaymentRequest request,
            @CurrentUser Long callerUserId) {

        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(ApiResponse.success(
                adminPaymentService.refund(gatewayOrderId, reason, callerUserId)));
    }
}
