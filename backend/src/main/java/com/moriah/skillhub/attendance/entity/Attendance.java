package com.moriah.skillhub.attendance.entity;

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

/**
 * Unique {@code (standup_id, user_id)} — build-plan.md feature 13: "double check-in is
 * idempotent, not an error." {@code markedBy} is null for a self check-in or an auto-marked
 * {@code ABSENT} row, set for a PM override. {@code isAutoMarked} distinguishes {@code
 * AttendanceFinalisationJob}'s writes from every other path — never set anywhere else.
 */
@Entity
@Table(name = "attendance", uniqueConstraints =
    @UniqueConstraint(columnNames = {"standup_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Attendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "standup_id")
    private Standup standup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "blocker_notes", columnDefinition = "TEXT")
    private String blockerNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by")
    private User markedBy;

    @Column(name = "is_auto_marked", nullable = false)
    private boolean autoMarked = false;
}
