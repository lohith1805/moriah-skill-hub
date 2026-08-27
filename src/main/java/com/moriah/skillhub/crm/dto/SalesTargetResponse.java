package com.moriah.skillhub.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesTargetResponse(
        Long id,
        String agentUuid,
        LocalDate periodMonth,
        Integer callsTarget,
        Integer callsMade,
        Integer conversionsTarget,
        Integer conversionsMade,
        BigDecimal revenueTarget,
        BigDecimal revenueAchieved
) {
}
