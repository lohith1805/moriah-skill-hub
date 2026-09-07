package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/leads/inbound} — the public landing-page capture form. No agent context:
 * the lead lands unassigned for a lead-gen agent to pick up. {@code source} / {@code leadType}
 * default to {@code LANDING_PAGE} / {@code "B2C"} when omitted; {@code message} is stored as a
 * NOTE activity on the lead.
 */
public record InboundLeadRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 20) String phone,
        @Size(max = 2000) String message,
        @Size(max = 50) String leadType,
        LeadSource source
) {
}
