package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadSource;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateLeadRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 20) String phone,
        @NotNull LeadSource source,
        @NotBlank @Size(max = 50) String leadType,
        @Size(max = 150) String institution,
        Long interestedPlanId,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal dealValue
) {
}
