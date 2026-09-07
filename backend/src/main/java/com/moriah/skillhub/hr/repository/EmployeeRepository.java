package com.moriah.skillhub.hr.repository;

import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByEmployeeCode(String employeeCode);

    /** {@code GET /api/v1/hr/employees}. All filters optional. {@code user}/{@code
     * reportingManager} eager so the response builds without an N+1. */
    @Query("""
            SELECT e FROM Employee e
             WHERE (:status IS NULL OR e.status = :status)
               AND (:department IS NULL OR LOWER(e.department) = LOWER(:department))
               AND (:search IS NULL
                    OR LOWER(e.user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    @EntityGraph(attributePaths = {"user", "reportingManager"})
    Page<Employee> search(@Param("status") EmployeeStatus status,
                          @Param("department") String department,
                          @Param("search") String search,
                          Pageable pageable);

    @EntityGraph(attributePaths = {"user", "reportingManager"})
    Optional<Employee> findWithAssociationsById(Long id);

    /** Batch fetch with {@code user} eagerly joined — used by {@code PayrollService.generate} to
     * avoid one {@code findById} + one lazy {@code user} load per payroll line. */
    @Query("SELECT e FROM Employee e JOIN FETCH e.user WHERE e.id IN :ids")
    List<Employee> findAllWithUserByIdIn(@Param("ids") Collection<Long> ids);
}
