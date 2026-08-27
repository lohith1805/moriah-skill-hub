package com.moriah.skillhub.crm.repository;

import com.moriah.skillhub.crm.entity.SalesTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SalesTargetRepository extends JpaRepository<SalesTarget, Long> {

    Optional<SalesTarget> findByAgentIdAndPeriodMonth(Long agentId, LocalDate periodMonth);
}
