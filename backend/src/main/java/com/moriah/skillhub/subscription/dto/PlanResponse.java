package com.moriah.skillhub.subscription.dto;

import java.math.BigDecimal;

public record PlanResponse(
        String code,
        String name,
        BigDecimal priceInr,
        Integer tierRank,
        Integer durationDays,
        boolean mentorSupport,
        boolean allowsBatch,
        boolean allowsSprints,
        boolean allowsPip,
        boolean allowsInternshipLetter,
        boolean allowsClientProject
) {
}
