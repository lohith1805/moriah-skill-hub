package com.moriah.skillhub.talent.repository;

import com.moriah.skillhub.talent.entity.RecruitmentRequest;
import com.moriah.skillhub.talent.entity.RecruitmentRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecruitmentRequestRepository extends JpaRepository<RecruitmentRequest, Long> {

    /** {@code GET /api/v1/recruitment-requests}. {@code requestedBy} is {@code null} for a
     * privileged caller (ADMIN/HR_MANAGER see everything) and the caller's own id otherwise.
     * {@code status} optional. */
    @Query("""
            SELECT r FROM RecruitmentRequest r
             WHERE (:status IS NULL OR r.status = :status)
               AND (:requestedBy IS NULL OR r.requestedBy = :requestedBy)
            """)
    Page<RecruitmentRequest> search(@Param("status") RecruitmentRequestStatus status,
                                    @Param("requestedBy") Long requestedBy,
                                    Pageable pageable);
}
