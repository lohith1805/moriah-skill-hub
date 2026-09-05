package com.moriah.skillhub.client.repository;

import com.moriah.skillhub.client.entity.RequirementDocument;
import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.client.entity.RequirementDocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RequirementDocumentRepository extends JpaRepository<RequirementDocument, Long> {

    /** Shared by the BA list ({@code GET /ba/documents}) and the developer list ({@code GET
     * /dev/requirement-documents}) — same rows, role gated on the controller. Both filters
     * optional, {@code null} drops the predicate (the {@code UserRepository.search} idiom).
     * {@code authoredBy}/{@code approvedBy}/{@code devReviewedBy} eager so the response builds
     * without an N+1. */
    @Query("""
            SELECT d FROM RequirementDocument d
             WHERE (:clientProjectId IS NULL OR d.clientProject.id = :clientProjectId)
               AND (:status IS NULL OR d.status = :status)
            """)
    @EntityGraph(attributePaths = {"clientProject", "authoredBy", "approvedBy", "devReviewedBy", "rejectedBy"})
    Page<RequirementDocument> search(@Param("clientProjectId") Long clientProjectId,
                                     @Param("status") RequirementDocumentStatus status,
                                     Pageable pageable);

    /** {@code RequirementDocumentService#create}'s versioning rule: "a new document for the
     * same (client_project_id, doc_type) pair gets version = max existing version for that pair
     * + 1" (build-plan.md feature 21 decision). {@code MAX} over zero rows returns {@code NULL},
     * so an empty {@code Optional} means "no prior version" — the caller starts at 1. */
    @Query("""
            SELECT MAX(d.version) FROM RequirementDocument d
             WHERE d.clientProject.id = :clientProjectId AND d.docType = :docType
            """)
    Optional<Integer> findMaxVersion(@Param("clientProjectId") Long clientProjectId,
            @Param("docType") RequirementDocumentType docType);

    /** {@code RequirementDocumentService#approve} needs {@code authoredBy}/{@code approvedBy}
     * eager for the response — {@code @EntityGraph}, same idiom as {@code
     * CertificateRepository.findWithAssociationsById}. */
    @EntityGraph(attributePaths = {"clientProject", "authoredBy", "approvedBy", "devReviewedBy", "rejectedBy"})
    @Query("SELECT d FROM RequirementDocument d WHERE d.id = :id")
    Optional<RequirementDocument> findWithAssociationsById(@Param("id") Long id);
}
