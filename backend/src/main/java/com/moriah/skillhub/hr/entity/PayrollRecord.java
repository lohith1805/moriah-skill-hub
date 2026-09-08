package com.moriah.skillhub.hr.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "payroll_records", uniqueConstraints =
        @UniqueConstraint(columnNames = {"employee_id", "period_month"}))
@Getter
@Setter
@NoArgsConstructor
public class PayrollRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(name = "working_days", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer workingDays;

    @Column(name = "present_days", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer presentDays;

    /** Loss-of-pay days: working days the salaried employee was neither present nor on approved
     * PAID leave (SICK/CASUAL/EARNED) — i.e. unpaid leave and plain absence alike. The gross is
     * {@code baseSalary * (presentDays + paidLeaveDays) / workingDays}, so this is
     * {@code workingDays - presentDays - paidLeaveDays} (floored at 0). {@code 0} for hourly
     * employees. Recorded so the payslip / API response can show the breakdown. */
    @Column(name = "lop_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal lopDays = BigDecimal.ZERO;

    @Column(name = "session_hours", precision = 6, scale = 2)
    private BigDecimal sessionHours;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deductions;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "payslip_key")
    private String payslipKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayrollStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;
}
