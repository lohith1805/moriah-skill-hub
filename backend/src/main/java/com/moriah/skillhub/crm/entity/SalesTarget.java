package com.moriah.skillhub.crm.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "sales_targets", uniqueConstraints =
        @UniqueConstraint(columnNames = {"agent_id", "period_month"}))
@Getter
@Setter
@NoArgsConstructor
public class SalesTarget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private User agent;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    // columnDefinition matches V12's SMALLINT UNSIGNED exactly — Hibernate's default for an
    // Integer column is INTEGER, not SMALLINT (same fix as PipRule.windowDays).
    @Column(name = "calls_target", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer callsTarget;

    @Column(name = "calls_made", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer callsMade;

    @Column(name = "conversions_target", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer conversionsTarget;

    @Column(name = "conversions_made", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer conversionsMade;

    @Column(name = "revenue_target", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenueTarget;

    @Column(name = "revenue_achieved", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenueAchieved;
}
