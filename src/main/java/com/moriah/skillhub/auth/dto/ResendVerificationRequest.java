package com.moriah.skillhub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/v1/auth/resend-verification}. Same shape as
 * {@link ForgotPasswordRequest} and, like it, the endpoint responds identically whether or not
 * the address maps to an account still awaiting verification — no account-enumeration signal. */
public record ResendVerificationRequest(@NotBlank @Email String email) {
}
