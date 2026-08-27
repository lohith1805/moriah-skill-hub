package com.moriah.skillhub.hr.repository;

import com.moriah.skillhub.hr.entity.Employee;
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

    /** Batch fetch with {@code user} eagerly joined — used by {@code PayrollService.generate} to
     * avoid one {@code findById} + one lazy {@code user} load per payroll line. */
    @Query("SELECT e FROM Employee e JOIN FETCH e.user WHERE e.id IN :ids")
    List<Employee> findAllWithUserByIdIn(@Param("ids") Collection<Long> ids);
}
