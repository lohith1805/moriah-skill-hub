package com.moriah.skillhub.pip.dto;

import com.moriah.skillhub.pip.entity.PipRuleCode;
import com.moriah.skillhub.pip.entity.PipSeverity;

import java.math.BigDecimal;

public record PipRuleResponse(
        PipRuleCode ruleCode,
        String description,
        BigDecimal thresholdValue,
        Integer windowDays,
        PipSeverity severity,
        boolean active
) {
}
