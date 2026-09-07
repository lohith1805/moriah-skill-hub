package com.moriah.skillhub.hr.repository;

import com.moriah.skillhub.hr.entity.DisciplinaryAction;
import com.moriah.skillhub.hr.entity.DisciplinarySeverity;
import com.moriah.skillhub.hr.entity.DisciplinaryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DisciplinaryActionRepository extends JpaRepository<DisciplinaryAction, Long> {

    @Query("""
            SELECT d FROM DisciplinaryAction d
             WHERE (:status IS NULL OR d.status = :status)
               AND (:severity IS NULL OR d.severity = :severity)
               AND (:employeeId IS NULL OR d.employee.id = :employeeId)
            """)
    @EntityGraph(attributePaths = {"employee", "employee.user"})
    Page<DisciplinaryAction> search(@Param("status") DisciplinaryStatus status,
                                    @Param("severity") DisciplinarySeverity severity,
                                    @Param("employeeId") Long employeeId,
                                    Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "employee.user"})
    Optional<DisciplinaryAction> findWithEmployeeById(Long id);
}
