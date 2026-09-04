package com.moriah.skillhub.client.repository;

import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.entity.ClientProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientProjectRepository extends JpaRepository<ClientProject, Long> {

    /** {@code GET /api/v1/clients/projects}. {@code clientId} null = every client's projects
     * (BA / ADMIN oversight); set = a CLIENT's own. {@code status} optional. Newest first.
     * {@code client} fetched for the response's {@code clientName}. */
    @Query("""
            SELECT p FROM ClientProject p
              JOIN FETCH p.client c
              LEFT JOIN FETCH p.targetBatch b
             WHERE (:clientId IS NULL OR c.id = :clientId)
               AND (:status IS NULL OR p.status = :status)
             ORDER BY p.submittedAt DESC
            """)
    Page<ClientProject> search(@Param("clientId") Long clientId,
                               @Param("status") ClientProjectStatus status,
                               Pageable pageable);

    /** {@code ClientProjectService#progress}'s ownership check reads {@code client.user.id}
     * without an extra round trip — {@code @EntityGraph} on the dotted path {@code
     * client.user} eager-loads through the association in one query, same idiom as {@code
     * CertificateRepository.findWithAssociationsById}. */
    @EntityGraph(attributePaths = {"client", "client.user", "targetBatch"})
    @Query("SELECT p FROM ClientProject p WHERE p.id = :id")
    Optional<ClientProject> findWithClientById(@Param("id") Long id);
}
