package com.moriah.skillhub.hr.repository;

import com.moriah.skillhub.hr.entity.LeaveRequest;
import com.moriah.skillhub.hr.entity.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** {@code GET /api/v1/hr/leaves} — {@code user}/{@code approvedBy} fetched in the same query
     * (both feed {@code LeaveService#toResponse}). Four variants for the (userId?, status?) filter
     * matrix; {@code findAll(Pageable)} is overridden with the same graph for the HR "everyone,
     * every status" case. */
    @Override
    @EntityGraph(attributePaths = {"user", "approvedBy"})
    Page<LeaveRequest> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    Page<LeaveRequest> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    Page<LeaveRequest> findByStatus(LeaveStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    Page<LeaveRequest> findByUserIdAndStatus(Long userId, LeaveStatus status, Pageable pageable);

    /** {@code LeaveService}'s overlap-on-approve check (build-plan.md feature 19: "overlapping
     * approved leave returns 409") — standard interval-overlap predicate, backed by {@code
     * idx_leave_requests_user_status_dates}. Excludes {@code excludeId} so a leave never
     * "overlaps itself" when it's the very row being approved. */
    @Query("""
            SELECT COUNT(l) > 0 FROM LeaveRequest l
            WHERE l.user.id = :userId AND l.status = 'APPROVED' AND l.id <> :excludeId
              AND l.fromDate <= :toDate AND l.toDate >= :fromDate
            """)
    boolean existsOverlappingApproved(@Param("userId") Long userId, @Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate, @Param("excludeId") Long excludeId);
}
