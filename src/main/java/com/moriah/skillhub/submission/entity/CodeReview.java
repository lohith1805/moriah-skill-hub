package com.moriah.skillhub.submission.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** {@code submission} is a real {@code @ManyToOne} — {@link TaskSubmission} is in this same
 * {@code submission/} package. {@code reviewer} is a real {@code @ManyToOne User} — shared
 * kernel. */
@Entity
@Table(name = "code_reviews")
@Getter
@Setter
@NoArgsConstructor
public class CodeReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id")
    private TaskSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    // TINYINT UNSIGNED in V7 — see Sprint.sprintNumber's Javadoc.
    @Column(columnDefinition = "TINYINT UNSIGNED")
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewVerdict verdict;

    @Column(columnDefinition = "TEXT")
    private String comments;

    // Pre-serialized JSON text — same treatment as Notification.payload/WebhookEvent.payload.
    @Column(name = "inline_comments", columnDefinition = "JSON")
    private String inlineComments;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt = Instant.now();
}
