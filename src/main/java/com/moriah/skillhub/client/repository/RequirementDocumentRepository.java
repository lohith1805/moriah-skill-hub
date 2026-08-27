package com.moriah.skillhub.client.repository;

import com.moriah.skillhub.client.entity.RequirementDocument;
import com.moriah.skillhub.client.entity.RequirementDocumentType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RequirementDocumentRepository extends JpaRepository<RequirementDocument, Long> {

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
    @EntityGraph(attributePaths = {"clientProject", "authoredBy", "approvedBy"})
    @Query("SELECT d FROM RequirementDocument d WHERE d.id = :id")
    Optional<RequirementDocument> findWithAssociationsById(@Param("id") Long id);
}
