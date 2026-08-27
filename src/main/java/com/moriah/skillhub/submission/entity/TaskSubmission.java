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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * {@code taskId} is a bare {@code Long}, not a real {@code @ManyToOne Task} — unlike {@code
 * Batch}, which feature 11 promoted to a shared-kernel type because code-standards.md's own
 * canonical {@code Sprint} example already does so, {@code Task} has no such precedent.
 * {@code submission/} calls {@code TaskService}'s public methods (e.g. {@code markInReview}) for
 * anything that needs the actual task, matching {@code Payment.planId}'s established bare-id
 * cross-package pattern. {@code user} is a real {@code @ManyToOne} — the shared-kernel exception.
 */
@Entity
@Table(name = "task_submissions", uniqueConstraints =
    @UniqueConstraint(columnNames = {"task_id", "user_id", "attempt_number"}))
@Getter
@Setter
@NoArgsConstructor
public class TaskSubmission extends BaseEntity {

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    // TINYINT UNSIGNED in V7 — see Sprint.sprintNumber's Javadoc for why this needs an explicit
    // columnDefinition override.
    @Column(name = "attempt_number", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private int attemptNumber = 1;

    @Column(name = "pr_url", length = 500)
    private String prUrl;

    @Column(name = "repo_owner", length = 100)
    private String repoOwner;

    @Column(name = "repo_name", length = 100)
    private String repoName;

    @Column(name = "pr_number", columnDefinition = "INT UNSIGNED")
    private Integer prNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "pr_state", length = 20)
    private PrState prState;

    @Column(name = "commit_count", columnDefinition = "SMALLINT UNSIGNED")
    private Integer commitCount;

    // CHAR(40) — a Git SHA-1 hash is always exactly 40 hex characters. Hibernate's default for a
    // String column is VARCHAR, not CHAR (the same fix as every *_hash/uuid column in this
    // project — see RefreshToken.tokenHash).
    @Column(name = "latest_commit_sha", columnDefinition = "CHAR(40)")
    private String latestCommitSha;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    /** {@code null} means "not yet verified" — either still pending the initial call, or GitHub
     * returned a 5xx and {@code SubmissionVerificationRetryJob} hasn't succeeded yet
     * (build-plan.md feature 12: "An outage never blocks a student"). */
    @Column(name = "verified_at")
    private Instant verifiedAt;
}
