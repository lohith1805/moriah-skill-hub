package com.moriah.skillhub.client.repository;

import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.entity.ClientProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientProjectRepository extends JpaRepository<ClientProject, Long> {

    /** {@code GET /api/v1/clients/projects}. {@code clientId} null = every client's projects
     * (BA / ADMIN oversight); set = a CLIENT's own. {@code status} optional, {@code assignedBaId}
     * optional (a BA's own "assigned to me" default view — see {@code ClientProjectService#list}).
     * Newest first. {@code client}/{@code assignedBa}/{@code assignedDeveloper} fetched for the
     * response. */
    @Query("""
            SELECT p FROM ClientProject p
              JOIN FETCH p.client c
              LEFT JOIN FETCH p.targetBatch b
              LEFT JOIN FETCH p.assignedBa ba
              LEFT JOIN FETCH p.assignedDeveloper dev
             WHERE (:clientId IS NULL OR c.id = :clientId)
               AND (:status IS NULL OR p.status = :status)
               AND (:assignedBaId IS NULL OR p.assignedBa.id = :assignedBaId OR p.assignedBa IS NULL)
             ORDER BY p.submittedAt DESC
            """)
    Page<ClientProject> search(@Param("clientId") Long clientId,
                               @Param("status") ClientProjectStatus status,
                               @Param("assignedBaId") Long assignedBaId,
                               Pageable pageable);

    /** {@code StaffAssignmentService}'s least-busy pick — one flat GROUP BY for every candidate
     * BA/developer's current open-project count, not a per-candidate repository call in a loop
     * (code-standards.md "N+1 Prevention"). "Open" = {@code SUBMITTED} or {@code IN_PROGRESS};
     * {@code COMPLETED} projects don't count against anyone's workload. */
    @Query("SELECT p.assignedBa.id AS userId, COUNT(p) AS openCount FROM ClientProject p " +
            "WHERE p.assignedBa IS NOT NULL AND p.status IN :openStatuses GROUP BY p.assignedBa.id")
    List<StaffOpenProjectCount> countOpenByAssignedBa(@Param("openStatuses") List<ClientProjectStatus> openStatuses);

    @Query("SELECT p.assignedDeveloper.id AS userId, COUNT(p) AS openCount FROM ClientProject p " +
            "WHERE p.assignedDeveloper IS NOT NULL AND p.status IN :openStatuses GROUP BY p.assignedDeveloper.id")
    List<StaffOpenProjectCount> countOpenByAssignedDeveloper(@Param("openStatuses") List<ClientProjectStatus> openStatuses);

    interface StaffOpenProjectCount {
        Long getUserId();
        Long getOpenCount();
    }

    /** {@code ClientProjectService#progress}'s ownership check reads {@code client.user.id}
     * without an extra round trip — {@code @EntityGraph} on the dotted path {@code
     * client.user} eager-loads through the association in one query, same idiom as {@code
     * CertificateRepository.findWithAssociationsById}. */
    @EntityGraph(attributePaths = {"client", "client.user", "targetBatch"})
    @Query("SELECT p FROM ClientProject p WHERE p.id = :id")
    Optional<ClientProject> findWithClientById(@Param("id") Long id);
}
