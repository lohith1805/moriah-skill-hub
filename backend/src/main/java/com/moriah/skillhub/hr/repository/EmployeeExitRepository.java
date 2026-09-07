package com.moriah.skillhub.hr.repository;

import com.moriah.skillhub.hr.entity.EmployeeExit;
import com.moriah.skillhub.hr.entity.EmployeeExitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeExitRepository extends JpaRepository<EmployeeExit, Long> {

    /** {@code GET /api/v1/hr/exits}. Both filters optional. {@code employee} (and its {@code
     * user}) eager so the response builds without an N+1. */
    @Query("""
            SELECT x FROM EmployeeExit x
             WHERE (:status IS NULL OR x.status = :status)
               AND (:employeeId IS NULL OR x.employee.id = :employeeId)
            """)
    @EntityGraph(attributePaths = {"employee", "employee.user"})
    Page<EmployeeExit> search(@Param("status") EmployeeExitStatus status,
                              @Param("employeeId") Long employeeId,
                              Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "employee.user"})
    Optional<EmployeeExit> findWithEmployeeById(Long id);

    /** An employee may have only one exit that is not terminal — guards a double-initiate. */
    boolean existsByEmployeeIdAndStatusIn(Long employeeId, java.util.Collection<EmployeeExitStatus> statuses);
}
