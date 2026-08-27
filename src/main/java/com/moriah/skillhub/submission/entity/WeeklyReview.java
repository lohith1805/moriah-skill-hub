package com.moriah.skillhub.submission.entity;

import com.moriah.skillhub.batch.entity.Batch;
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

/** {@code batch} is a real {@code @ManyToOne Batch} — the shared-kernel exception feature 11
 * established (code-standards.md's own canonical example already treats {@code Batch} this way).
 * {@code sprintId} stays a bare {@code Long} — {@code Sprint} itself has no such precedent for
 * being referenced from outside {@code sprint/}, matching {@code Task.projectId}'s bare-id
 * treatment of an unestablished cross-package type. */
@Entity
@Table(name = "weekly_reviews", uniqueConstraints =
    @UniqueConstraint(columnNames = {"user_id", "week_start"}))
@Getter
@Setter
@NoArgsConstructor
public class WeeklyReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "sprint_id", nullable = false)
    private Long sprintId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WeeklyRating rating;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt = Instant.now();
}
