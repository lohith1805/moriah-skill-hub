package com.moriah.skillhub.hr.repository;

import com.moriah.skillhub.hr.entity.HrDocument;
import com.moriah.skillhub.hr.entity.HrDocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HrDocumentRepository extends JpaRepository<HrDocument, Long> {

    /** The newest upload of a given type for a user — a re-upload after a rejection creates a new
     * row, so "is this document type verified?" is a question about the latest one. */
    Optional<HrDocument> findFirstByUserIdAndDocumentTypeOrderByCreatedAtDesc(Long userId, String documentType);

    /** {@code GET /api/v1/hr/documents} — optional (userId, status, documentType) filter, all
     * nullable. {@code user}/{@code verifiedBy} fetched in the same query (both feed {@code
     * HrDocumentService#toResponse}). */
    @EntityGraph(attributePaths = {"user", "verifiedBy"})
    @Query("""
            SELECT d FROM HrDocument d
            WHERE (:userId IS NULL OR d.user.id = :userId)
              AND (:status IS NULL OR d.verificationStatus = :status)
              AND (:docType IS NULL OR d.documentType = :docType)
            """)
    Page<HrDocument> search(@Param("userId") Long userId,
                            @Param("status") HrDocumentStatus status,
                            @Param("docType") String docType,
                            Pageable pageable);
}
