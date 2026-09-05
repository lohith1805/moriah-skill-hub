package com.moriah.skillhub.client.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Named {@code ClientProject}, not {@code Project} — {@code
 * com.moriah.skillhub.project.entity.Project} is feature 15's completely unrelated
 * content-catalog entity; architecture.md's own {@code client_projects} table name exists for
 * exactly this collision reason. {@code targetBatch} is a real, nullable {@code @ManyToOne
 * Batch} — the established shared-kernel exception ({@code Sprint.batch}, {@code
 * Certificate.batch}), nullable because build-plan.md feature 21 is explicit that a submitted
 * project may have no batch allocated yet ("If target_batch_id is null ... return an
 * empty/zeroed progress shape, not an error" — see {@code ClientProjectService#progress}).
 */
@Entity
@Table(name = "client_projects")
@Getter
@Setter
@NoArgsConstructor
public class ClientProject extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    /** Auto-assigned (round-robin, least open projects) the moment the client submits — see
     * {@code StaffAssignmentService}. {@code null} only when no active BUSINESS_ANALYST exists at
     * all; an admin fills it in by hand from the Client Project Assignments screen. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_ba_id")
    private User assignedBa;

    /** Auto-assigned the moment the BA first signs off a BRD/FRS for this project — a developer
     * has nothing to do before that, so unlike {@link #assignedBa} this stays {@code null} well
     * past submission (see {@code RequirementDocumentApprovalService}). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_developer_id")
    private User assignedDeveloper;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "scope_description", nullable = false, columnDefinition = "TEXT")
    private String scopeDescription;

    @Column(name = "budget_range", length = 50)
    private String budgetRange;

    /** Optional free-text context beyond the one-line {@link #scopeDescription} — company
     * background, links, "who to loop in," anything the client wants BA/Dev to know before the
     * kickoff discussion. */
    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_batch_id")
    private Batch targetBatch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientProjectStatus status = ClientProjectStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
