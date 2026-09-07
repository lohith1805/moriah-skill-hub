package com.moriah.skillhub.hr.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
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

import java.time.Instant;
import java.time.LocalDate;

/**
 * One staff member's attendance for one calendar day (unique {@code (user_id, work_date)} —
 * V36). Created by a self / web check-in, a biometric-device log an HR user records on someone's
 * behalf, or an HR status override. {@code markedBy} is null for a self check-in, set when
 * HR_MANAGER/ADMIN created or corrected the row. {@code checkedInAt} can be null for a pure
 * status override (e.g. HR marking someone ABSENT / ON_LEAVE).
 */
@Entity
@Table(name = "staff_attendance", uniqueConstraints =
    @UniqueConstraint(name = "uq_staff_attendance_user_date", columnNames = {"user_id", "work_date"}))
@Getter
@Setter
@NoArgsConstructor
public class StaffAttendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffAttendanceStatus status = StaffAttendanceStatus.PRESENT;

    @Column(length = 50)
    private String device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by")
    private User markedBy;

    @Column(length = 500)
    private String notes;
}
