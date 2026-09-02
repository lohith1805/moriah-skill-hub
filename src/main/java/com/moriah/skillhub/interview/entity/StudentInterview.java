package com.moriah.skillhub.interview.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One scheduled interview for a student (gap B1.8). {@code studentId} / {@code scheduledBy} are
 * bare user ids — this row only ever needs them to scope a query or an ownership check, the same
 * {@code Notification.userId} / {@code LearningResource.createdBy} reasoning. {@code feedback} /
 * {@code rating} are written after the interview via {@code PUT}.
 */
@Entity
@Table(name = "student_interviews")
@Getter
@Setter
@NoArgsConstructor
public class StudentInterview extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "scheduled_by", nullable = false)
    private Long scheduledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false, length = 20)
    private InterviewType interviewType;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    // columnDefinition matches V26's INT UNSIGNED exactly — same reasoning as SubscriptionPlan's
    // UNSIGNED columns.
    @Column(name = "duration_minutes", columnDefinition = "INT UNSIGNED")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InterviewMode mode;

    @Column(length = 255)
    private String location;

    @Column(name = "interviewer_name", length = 150)
    private String interviewerName;

    @Column(name = "meeting_link", length = 1000)
    private String meetingLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(columnDefinition = "TINYINT UNSIGNED")
    private Integer rating;
}
