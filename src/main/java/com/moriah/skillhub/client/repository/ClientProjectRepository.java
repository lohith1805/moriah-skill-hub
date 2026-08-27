package com.moriah.skillhub.client.repository;

import com.moriah.skillhub.client.entity.ClientProject;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientProjectRepository extends JpaRepository<ClientProject, Long> {

    /** {@code ClientProjectService#progress}'s ownership check reads {@code client.user.id}
     * without an extra round trip — {@code @EntityGraph} on the dotted path {@code
     * client.user} eager-loads through the association in one query, same idiom as {@code
     * CertificateRepository.findWithAssociationsById}. */
    @EntityGraph(attributePaths = {"client", "client.user", "targetBatch"})
    @Query("SELECT p FROM ClientProject p WHERE p.id = :id")
    Optional<ClientProject> findWithClientById(@Param("id") Long id);
}
