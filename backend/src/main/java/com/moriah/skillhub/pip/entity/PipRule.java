package com.moriah.skillhub.pip.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Config, not code — architecture.md: "Thresholds live here, not in Java." {@code
 * PipEvaluationService} reads {@code thresholdValue}/{@code isActive} at evaluation time; {@code
 * windowDays} is informational only, never read by an evaluator (see V11's own comment on that
 * column for why). */
@Entity
@Table(name = "pip_rules")
@Getter
@Setter
@NoArgsConstructor
public class PipRule extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", nullable = false, length = 30)
    private PipRuleCode ruleCode;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "threshold_value", nullable = false, precision = 6, scale = 2)
    private BigDecimal thresholdValue;

    @Column(name = "window_days", columnDefinition = "SMALLINT UNSIGNED")
    private Integer windowDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipSeverity severity;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
