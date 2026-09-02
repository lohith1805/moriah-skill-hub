package com.moriah.skillhub.client.entity;

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

/**
 * {@code clientProject} is nullable at the DB level (architecture.md), matched here with
 * {@code optional = true} — but {@code RequirementDocumentService#create} always sets one
 * ({@code CreateRequirementDocumentRequest.clientProjectId} is {@code @NotNull}); the
 * nullability exists only to mirror the schema exactly for a possible future "general" document
 * not tied to a specific project, which no route in this feature creates. {@code fileKey} is
 * likewise schema-only — {@code content} (LONGTEXT) is this feature's one storage mechanism; no
 * upload endpoint exists in build-plan.md's feature 21 list to ever populate {@code fileKey}.
 * {@code version} is per-{@code (clientProject, docType)} pair, computed and enforced in
 * {@code RequirementDocumentService#create} (application-level, not a DB unique constraint — see
 * V15__ba_client.sql's own comment for why).
 */
@Entity
@Table(name = "requirement_documents")
@Getter
@Setter
@NoArgsConstructor
public class RequirementDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "client_project_id")
    private ClientProject clientProject;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private RequirementDocumentType docType;

    @Column(nullable = false, length = 200)
    private String title;

    // SMALLINT UNSIGNED in V15 — plain Integer defaults to signed INTEGER and fails
    // ddl-auto: validate (the recurring UNSIGNED/Hibernate mismatch first documented on
    // SubscriptionPlan.tierRank, feature 07; same fix as Sprint.sprintNumber).
    @Column(nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer version;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "file_key", length = 255)
    private String fileKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequirementDocumentStatus status = RequirementDocumentStatus.IN_REVIEW;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "authored_by")
    private User authoredBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    /** Set when a DEVELOPER acknowledges the requirement through {@code POST /api/v1/dev/
     * requirement-documents/{id}/acknowledge} (gap B1.16). Orthogonal to {@link #status}, which
     * is the BA approval axis. {@code null} = not yet acknowledged. Stamped once; a re-acknowledge
     * keeps the original timestamp. */
    @Column(name = "dev_reviewed_at")
    private java.time.Instant devReviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dev_reviewed_by")
    private User devReviewedBy;
}
