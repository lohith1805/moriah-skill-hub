package com.moriah.skillhub.client.repository;

import com.moriah.skillhub.client.entity.Client;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    /** {@code ClientProjectService#create}'s "resolve the caller's own client_id via
     * clients.user_id = callerUserId" lookup (build-plan.md feature 21 decision). {@code
     * @EntityGraph} on {@code user} — cheap here (single-row lookup, not a paginated list) and
     * saves a future caller of this method an extra lazy round trip if it ever needs the linked
     * user beyond just resolving the client id. */
    @EntityGraph(attributePaths = "user")
    Optional<Client> findByUserId(Long userId);
}
