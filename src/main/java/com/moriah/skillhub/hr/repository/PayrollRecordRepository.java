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

    /** Batch form of {@link #existsByEmployeeIdAndPeriodMonth} — one query for an entire payroll
     * batch instead of one per line. */
    @Query("SELECT p.employee.id FROM PayrollRecord p WHERE p.periodMonth = :periodMonth AND p.employee.id IN :employeeIds")
    List<Long> findEmployeeIdsAlreadyGenerated(@Param("periodMonth") LocalDate periodMonth, @Param("employeeIds") Collection<Long> employeeIds);
}
