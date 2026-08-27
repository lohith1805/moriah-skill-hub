package com.moriah.skillhub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 20) String phone,
        // BCrypt truncates input silently beyond 72 bytes — cap well under that.
        @NotBlank @Size(min = 8, max = 64) String password,
        @Pattern(regexp = "^[A-Za-z0-9-]{1,39}$", message = "must be a valid GitHub username")
        String githubUsername
) {
}
