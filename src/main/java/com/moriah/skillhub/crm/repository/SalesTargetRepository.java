package com.moriah.skillhub.crm.repository;

import com.moriah.skillhub.crm.entity.SalesTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.EntityGraph;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesTargetRepository extends JpaRepository<SalesTarget, Long> {

    Optional<SalesTarget> findByAgentIdAndPeriodMonth(Long agentId, LocalDate periodMonth);

    /** All agents' targets for one month — feeds the leaderboard's quota columns. {@code agent}
     * eager so {@code LeadService.leaderboard} can key by {@code agent.id} without an N+1. */
    @EntityGraph(attributePaths = "agent")
    List<SalesTarget> findByPeriodMonth(LocalDate periodMonth);
}
