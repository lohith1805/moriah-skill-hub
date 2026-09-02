package com.moriah.skillhub.client.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A BA-coordinated meeting (gap B1.14). {@code clientProject} is a nullable same-package
 * {@code @ManyToOne} — mirrors {@link RequirementDocument#getClientProject()}. {@code createdBy}
 * is a bare user id (content row, ownership check only). {@code minutes} is the post-meeting
 * write-up, populated via {@code PUT} once the meeting is {@code COMPLETED}.
 */
@Entity
@Table(name = "ba_meetings")
@Getter
@Setter
@NoArgsConstructor
public class BaMeeting extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String agenda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_project_id")
    private ClientProject clientProject;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    // columnDefinition matches V25's INT UNSIGNED exactly — same reasoning as SubscriptionPlan's
    // UNSIGNED columns.
    @Column(name = "duration_minutes", columnDefinition = "INT UNSIGNED")
    private Integer durationMinutes;

    @Column(length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BaMeetingStatus status = BaMeetingStatus.SCHEDULED;

    @Column(columnDefinition = "TEXT")
    private String minutes;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
