package com.moriah.skillhub.hr.repository;

import com.moriah.skillhub.hr.entity.PayrollRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PayrollRecordRepository extends JpaRepository<PayrollRecord, Long> {

    Optional<PayrollRecord> findByEmployeeIdAndPeriodMonth(Long employeeId, LocalDate periodMonth);

    boolean existsByEmployeeIdAndPeriodMonth(Long employeeId, LocalDate periodMonth);

    @Query(value = "SELECT p FROM PayrollRecord p JOIN FETCH p.employee e JOIN FETCH e.user WHERE p.periodMonth = :periodMonth",
            countQuery = "SELECT COUNT(p) FROM PayrollRecord p WHERE p.periodMonth = :periodMonth")
    Page<PayrollRecord> findByPeriodMonth(@Param("periodMonth") LocalDate periodMonth, Pageable pageable);

    /** {@code GET /api/v1/hr/payroll/me} — the caller's own payslips, newest first. {@code
     * employee}/{@code user} join-fetched so {@code PayrollService#toResponse} reads the code and
     * name without an N+1. Empty for a caller with no {@code employees} row. */
    @Query(value = "SELECT p FROM PayrollRecord p JOIN FETCH p.employee e JOIN FETCH e.user u "
            + "WHERE u.id = :userId ORDER BY p.periodMonth DESC",
            countQuery = "SELECT COUNT(p) FROM PayrollRecord p WHERE p.employee.user.id = :userId")
    Page<PayrollRecord> findByEmployeeUserIdOrderByPeriodMonthDesc(@Param("userId") Long userId, Pageable pageable);

    /** Batch form of {@link #existsByEmployeeIdAndPeriodMonth} — one query for an entire payroll
     * batch instead of one per line. */
    @Query("SELECT p.employee.id FROM PayrollRecord p WHERE p.periodMonth = :periodMonth AND p.employee.id IN :employeeIds")
    List<Long> findEmployeeIdsAlreadyGenerated(@Param("periodMonth") LocalDate periodMonth, @Param("employeeIds") Collection<Long> employeeIds);
}
