package com.moriah.skillhub.crm.repository;

import com.moriah.skillhub.crm.entity.Lead;
import com.moriah.skillhub.crm.entity.LeadSource;
import com.moriah.skillhub.crm.entity.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByDedupeHash(String dedupeHash);

    /** {@code phone} alone has no unique constraint (only {@code dedupe_hash}, email+phone
     * together, does) — two leads can share a phone with different emails. The webhook matches
     * inbound WhatsApp messages to whichever such lead was created most recently. */
    Optional<Lead> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    /** {@code LEFT JOIN FETCH} both lazy associations — {@code LeadService.toResponse} reads
     * {@code assignedAgent.fullName}/{@code convertedUser.id} for every row in the page; without
     * this, a 20-row page issues up to 40 extra lazy-load queries (code-standards.md's N+1 rule).
     * An explicit {@code countQuery} avoids Spring Data having to strip the fetch joins itself. */
    @Query(value = """
            SELECT l FROM Lead l
            LEFT JOIN FETCH l.assignedAgent
            LEFT JOIN FETCH l.convertedUser
            WHERE (:status IS NULL OR l.status = :status)
              AND (:agentId IS NULL OR l.assignedAgent.id = :agentId)
              AND (:source IS NULL OR l.source = :source)
            """,
            countQuery = """
            SELECT COUNT(l) FROM Lead l
            WHERE (:status IS NULL OR l.status = :status)
              AND (:agentId IS NULL OR l.assignedAgent.id = :agentId)
              AND (:source IS NULL OR l.source = :source)
            """)
    Page<Lead> search(@Param("status") LeadStatus status, @Param("agentId") Long agentId,
                       @Param("source") LeadSource source, Pageable pageable);
}
