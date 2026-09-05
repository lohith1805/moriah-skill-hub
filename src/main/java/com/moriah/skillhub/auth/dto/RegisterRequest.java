package com.moriah.skillhub.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code agreedToTerms} (NFR-05 / GDPR-DPDP): the Register page's "I agree to the Terms of
 * Service and Privacy Policy" checkbox was already required client-side, but nothing enforced or
 * recorded it server-side — a request with the box left unchecked (or sent straight to the API,
 * bypassing the UI entirely) registered exactly the same as one that agreed. {@code @AssertTrue}
 * makes {@code false} a 400, same as any other required field; {@code AuthService#register}
 * writes the actual consent record. */
public record RegisterRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 20) String phone,
        // BCrypt truncates input silently beyond 72 bytes — cap well under that.
        @NotBlank @Size(min = 8, max = 64) String password,
        @Pattern(regexp = "^[A-Za-z0-9-]{1,39}$", message = "must be a valid GitHub username")
        String githubUsername,
        @AssertTrue(message = "You must accept the Terms of Service and Privacy Policy to register.")
        boolean agreedToTerms
) {
}
