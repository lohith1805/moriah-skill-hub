package com.moriah.skillhub.client.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.RoleCode;
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
 * One required sign-off "slot" on a {@link RequirementDocument} — pre-created at authoring time
 * (see {@code RequirementDocumentService#create}'s {@code requiredRolesFor(docType)}). {@code
 * approverRole} reuses {@link RoleCode} rather than a bespoke enum — {@code CLIENT}/{@code
 * BUSINESS_ANALYST}/{@code DEVELOPER} are already exactly the values it needs, and every other
 * role-shaped column in this codebase (e.g. {@code CreateStaffRequest.roles}) does the same. A
 * slot is pending while {@link #approvedBy} is {@code null}; the parent document flips {@code
 * IN_REVIEW -> APPROVED} once every one of its slots is filled.
 */
@Entity
@Table(name = "requirement_document_approvals")
@Getter
@Setter
@NoArgsConstructor
public class RequirementDocumentApproval extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private RequirementDocument document;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role", nullable = false, length = 30)
    private RoleCode approverRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public RequirementDocumentApproval(RequirementDocument document, RoleCode approverRole) {
        this.document = document;
        this.approverRole = approverRole;
    }
}
