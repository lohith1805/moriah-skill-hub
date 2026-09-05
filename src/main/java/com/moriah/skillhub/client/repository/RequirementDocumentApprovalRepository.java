package com.moriah.skillhub.client.repository;

import com.moriah.skillhub.client.entity.RequirementDocumentApproval;
import com.moriah.skillhub.user.entity.RoleCode;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RequirementDocumentApprovalRepository extends JpaRepository<RequirementDocumentApproval, Long> {

    @EntityGraph(attributePaths = {"approvedBy"})
    List<RequirementDocumentApproval> findByDocumentId(Long documentId);

    /** {@code RequirementDocumentService#toResponse}'s batched per-page lookup — one flat query
     * for a whole page of documents instead of one {@link #findByDocumentId} call per row
     * (code-standards.md "N+1 Prevention"). */
    @EntityGraph(attributePaths = {"approvedBy"})
    List<RequirementDocumentApproval> findByDocumentIdIn(Collection<Long> documentIds);

    Optional<RequirementDocumentApproval> findByDocumentIdAndApproverRole(Long documentId, RoleCode approverRole);

    /** Whether {@code approverRole} has EVER been the {@code BUSINESS_ANALYST} approver of a
     * BRD/FRS on this client project — the developer-auto-assignment trigger fires once, on the
     * first such approval, never again for the same project. */
    @Query("""
            SELECT COUNT(a) > 0 FROM RequirementDocumentApproval a
              JOIN a.document d
             WHERE d.clientProject.id = :clientProjectId
               AND a.approverRole = 'BUSINESS_ANALYST'
               AND a.approvedBy IS NOT NULL
            """)
    boolean existsPriorBusinessAnalystApproval(@Param("clientProjectId") Long clientProjectId);

    /** {@code GET /api/v1/requirement-documents/pending-my-approval} — every document where
     * {@code approverRole}'s slot is still open. The caller-specific "is this actually my slot"
     * check (client owns the project / is the assigned developer / holds the role and isn't the
     * author) happens in the service, same split {@code ClientProjectService#list} already uses
     * for its own role-scoped filtering. */
    @EntityGraph(attributePaths = {"document", "document.clientProject", "document.authoredBy"})
    List<RequirementDocumentApproval> findByApproverRoleAndApprovedByIsNull(RoleCode approverRole);
}
