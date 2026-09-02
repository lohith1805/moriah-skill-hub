package com.moriah.skillhub.interview.repository;

import com.moriah.skillhub.interview.entity.InterviewStatus;
import com.moriah.skillhub.interview.entity.InterviewType;
import com.moriah.skillhub.interview.entity.StudentInterview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentInterviewRepository extends JpaRepository<StudentInterview, Long> {

    /** Staff list ({@code GET /api/v1/interviews}). All filters optional — a {@code null} drops
     * the predicate (the {@code UserRepository.search} idiom). {@code studentId} is resolved from
     * the {@code ?studentUuid} query param in the service. */
    @Query("""
            SELECT i FROM StudentInterview i
             WHERE (:status IS NULL OR i.status = :status)
               AND (:type IS NULL OR i.interviewType = :type)
               AND (:studentId IS NULL OR i.studentId = :studentId)
            """)
    Page<StudentInterview> search(@Param("status") InterviewStatus status,
                                  @Param("type") InterviewType type,
                                  @Param("studentId") Long studentId,
                                  Pageable pageable);

    /** {@code GET /api/v1/interviews/me} — the caller student's own interviews. */
    Page<StudentInterview> findByStudentId(Long studentId, Pageable pageable);
}
