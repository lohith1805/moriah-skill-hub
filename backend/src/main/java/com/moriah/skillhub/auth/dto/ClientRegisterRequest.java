package com.moriah.skillhub.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/register/client} — corporate-client self-registration. Re-opens
 * build-plan.md feature 21's "a client cannot self-register" (frontend-integration decision,
 * 2026-09-02): the account is created in {@code PENDING_APPROVAL} and cannot log in until an
 * ADMIN approves it at {@code POST /api/v1/admin/client-requests/{uuid}/approve}. {@code
 * POST /api/v1/clients} (ADMIN-provisioned, immediately active) is unchanged.
 * <p>
 * {@code agreedToTerms} — same NFR-05 consent enforcement as {@code RegisterRequest}, see its own
 * Javadoc.
 */
public record ClientRegisterRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 150) String companyName,
        @Size(max = 100) String industry,
        @AssertTrue(message = "You must accept the Terms of Service and Privacy Policy to register.")
        boolean agreedToTerms
) {
}
