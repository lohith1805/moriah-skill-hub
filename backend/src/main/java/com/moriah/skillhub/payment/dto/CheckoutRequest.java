package com.moriah.skillhub.payment.dto;

import com.moriah.skillhub.payment.entity.PaymentGateway;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code planCode} identifies the plan the same way {@code RoleCode} identifies a role
 * elsewhere in this project — never a raw {@code subscription_plans.id}. {@code couponCode} is
 * optional. Jackson rejects an invalid {@code gateway} value with the existing 400
 * {@code VALIDATION_FAILED} path (malformed-body handling already wired in feature 03) before
 * this ever reaches a service. {@code trackCode} is `/architect feature 10`'s decision: captured
 * here, not on the profile, so {@code BatchAllocationService.allocate} has it the moment the
 * payment webhook fires — free-text, matching {@code batches.track_code}'s own {@code
 * VARCHAR(30)} with no fixed catalog. */
public record CheckoutRequest(
        @NotBlank String planCode,
        @NotNull PaymentGateway gateway,
        String couponCode,
        @NotBlank @Size(max = 30) String trackCode
) {
}
