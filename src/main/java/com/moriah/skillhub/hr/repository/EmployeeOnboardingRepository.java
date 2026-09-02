package com.moriah.skillhub.hr.repository;

import com.moriah.skillhub.hr.entity.EmployeeOnboarding;
import com.moriah.skillhub.hr.entity.OnboardingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeOnboardingRepository extends JpaRepository<EmployeeOnboarding, Long> {

    @Query("""
            SELECT o FROM EmployeeOnboarding o
             WHERE (:status IS NULL OR o.status = :status)
               AND (:employeeId IS NULL OR o.employee.id = :employeeId)
            """)
    @EntityGraph(attributePaths = {"employee", "employee.user"})
    Page<EmployeeOnboarding> search(@Param("status") OnboardingStatus status,
                                    @Param("employeeId") Long employeeId,
                                    Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "employee.user"})
    Optional<EmployeeOnboarding> findWithEmployeeById(Long id);

    boolean existsByEmployeeIdAndStatusIn(Long employeeId, java.util.Collection<OnboardingStatus> statuses);
}
