package com.moriah.skillhub.client.repository;

import com.moriah.skillhub.client.entity.Client;
import com.moriah.skillhub.client.entity.ClientStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    /** {@code GET /api/v1/clients} — the "choose a client company" picker (HR letter generation,
     * and any future such UI). {@code @EntityGraph} on {@code user} so {@code
     * ClientService#toResponse} reads {@code user.uuid} without a lazy round trip per row. */
    @EntityGraph(attributePaths = "user")
    List<Client> findByStatusOrderByCompanyNameAsc(ClientStatus status);

    /** {@code ClientProjectService#create}'s "resolve the caller's own client_id via
     * clients.user_id = callerUserId" lookup (build-plan.md feature 21 decision). {@code
     * @EntityGraph} on {@code user} — cheap here (single-row lookup, not a paginated list) and
     * saves a future caller of this method an extra lazy round trip if it ever needs the linked
     * user beyond just resolving the client id. */
    @EntityGraph(attributePaths = "user")
    Optional<Client> findByUserId(Long userId);

    /** Batch-load the {@code clients} rows for a page of client users — the client-approval
     * queue's per-row company details ({@code ClientApprovalService#list}), gathered in one
     * query the same way {@code AdminUserService#list} batch-loads roles. */
    List<Client> findByUserIdIn(Collection<Long> userIds);
}
