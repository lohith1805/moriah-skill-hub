package com.moriah.skillhub.payment;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.payment.dto.CheckoutRequest;
import com.moriah.skillhub.payment.dto.CheckoutResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    /** Any authenticated user may check out a plan — no role restriction beyond being logged in,
     * so the intent is made explicit here rather than relying only on SecurityConfig's default
     * (code-standards.md "Security Rules": a missing @PreAuthorize is a defect, not an oversight,
     * even where the check is "authenticated is enough"). */
    @PostMapping("/checkout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a payment order/session for a plan; never activates anything directly")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request, @CurrentUser Long callerUserId) {
        CheckoutResponse response = checkoutService.checkout(callerUserId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
