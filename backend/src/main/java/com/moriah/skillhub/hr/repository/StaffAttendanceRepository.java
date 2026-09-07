package com.moriah.skillhub.hr.repository;

import com.moriah.skillhub.hr.dto.StaffAttendanceSummaryProjection;
import com.moriah.skillhub.hr.entity.StaffAttendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StaffAttendanceRepository extends JpaRepository<StaffAttendance, Long> {

    Optional<StaffAttendance> findByUserIdAndWorkDate(Long userId, LocalDate workDate);

    /** {@code GET /api/v1/hr/attendance}. {@code userId} null = every staff member (HR view);
     * {@code from}/{@code to} null = no date bound on that side. Newest day first. */
    @Query("""
            SELECT sa FROM StaffAttendance sa
             WHERE (:userId IS NULL OR sa.user.id = :userId)
               AND (:from IS NULL OR sa.workDate >= :from)
               AND (:to   IS NULL OR sa.workDate <= :to)
             ORDER BY sa.workDate DESC, sa.checkedInAt DESC
            """)
    Page<StaffAttendance> search(@Param("userId") Long userId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 Pageable pageable);

    /** Per-user status counts over a date range — the monthly ledger roll-up. Employees with no
     * rows in the range simply don't appear here; {@code StaffAttendanceService} fills them in
     * as all-zero. */
    @Query("""
            SELECT sa.user.id AS userId,
                   SUM(CASE WHEN sa.status = com.moriah.skillhub.hr.entity.StaffAttendanceStatus.PRESENT  THEN 1 ELSE 0 END) AS presentDays,
                   SUM(CASE WHEN sa.status = com.moriah.skillhub.hr.entity.StaffAttendanceStatus.LATE     THEN 1 ELSE 0 END) AS lateDays,
                   SUM(CASE WHEN sa.status = com.moriah.skillhub.hr.entity.StaffAttendanceStatus.ABSENT   THEN 1 ELSE 0 END) AS absentDays,
                   SUM(CASE WHEN sa.status = com.moriah.skillhub.hr.entity.StaffAttendanceStatus.HALF_DAY THEN 1 ELSE 0 END) AS halfDays,
                   SUM(CASE WHEN sa.status = com.moriah.skillhub.hr.entity.StaffAttendanceStatus.ON_LEAVE THEN 1 ELSE 0 END) AS onLeaveDays,
                   COALESCE(SUM(CASE WHEN sa.checkedInAt IS NOT NULL AND sa.checkedOutAt IS NOT NULL
                                     THEN timestampdiff(minute, sa.checkedInAt, sa.checkedOutAt)
                                     ELSE 0 END), 0) AS workedMinutes
              FROM StaffAttendance sa
             WHERE sa.workDate BETWEEN :from AND :to
             GROUP BY sa.user.id
            """)
    List<StaffAttendanceSummaryProjection> summarise(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
