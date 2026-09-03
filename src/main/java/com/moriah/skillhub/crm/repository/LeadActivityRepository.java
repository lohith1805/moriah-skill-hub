package com.moriah.skillhub.crm.repository;

import com.moriah.skillhub.crm.entity.LeadActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, Long> {

    List<LeadActivity> findByLeadIdOrderByOccurredAtDesc(Long leadId);

    /** {@code GET /api/v1/leads/{id}/activities} — paginated, newest first. {@code agent} is
     * eager so {@code toActivityResponse} can read {@code agent.fullName} without an N+1. */
    @EntityGraph(attributePaths = "agent")
    Page<LeadActivity> findByLeadId(Long leadId, Pageable pageable);
}
