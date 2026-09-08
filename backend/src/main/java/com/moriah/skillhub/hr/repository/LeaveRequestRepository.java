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
import java.util.Collection;
import java.util.List;

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

    /** {@code PayrollService}'s loss-of-pay proration — every APPROVED {@code UNPAID} leave for
     * these users that touches {@code [monthStart, monthEnd]}. One flat query for the whole batch;
     * the per-month day count (a leave can straddle a month boundary) is computed in the service,
     * not here. {@code user} is fetched so the service can key the result by {@code user.id}
     * without an N+1. */
    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT l FROM LeaveRequest l
            WHERE l.user.id IN :userIds
              AND l.status = 'APPROVED' AND l.leaveType = 'UNPAID'
              AND l.fromDate <= :monthEnd AND l.toDate >= :monthStart
            """)
    List<LeaveRequest> findApprovedUnpaidOverlappingMonth(@Param("userIds") Collection<Long> userIds,
            @Param("monthStart") LocalDate monthStart, @Param("monthEnd") LocalDate monthEnd);
}
