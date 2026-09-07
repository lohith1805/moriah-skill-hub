package com.moriah.skillhub.subscription.dto;

import com.moriah.skillhub.subscription.entity.SubscriptionStatus;

import java.time.LocalDate;

public record SubscriptionResponse(
        String planCode,
        String planName,
        LocalDate startDate,
        LocalDate endDate,
        SubscriptionStatus status,
        boolean autoRenew
) {
}
