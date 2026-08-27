package com.moriah.skillhub.hr.repository;

import com.moriah.skillhub.hr.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

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
