package com.moriah.skillhub.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/clients} — ADMIN-only (build-plan.md: "CLIENT users are provisioned by
 * ADMIN — a client cannot self-register"). {@code companyName}/{@code contactPerson}/{@code
 * email} double as the linked portal login's full name and email when {@code
 * provisionPortalLogin} is {@code true} — there is no separate "portal contact" sub-object,
 * since architecture.md's {@code clients} table already carries exactly the fields a login
 * needs (build-plan.md feature 21 decision: reuse the same fields rather than collecting them
 * twice). {@code provisionPortalLogin} is a primitive {@code boolean}, not {@code Boolean} — an
 * omitted JSON field deserializes to {@code false} (no portal login), matching the decision's
 * "if the request omits portal-login fields, just create the clients row with user_id null." */
public record CreateClientRequest(
        @NotBlank @Size(max = 150) String companyName,
        @NotBlank @Size(max = 150) String contactPerson,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 20) String phone,
        @Size(max = 100) String industry,
        boolean provisionPortalLogin
) {
}
