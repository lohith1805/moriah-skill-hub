package com.moriah.skillhub.crm.repository;

import java.math.BigDecimal;

/** Projection for {@link LeadRepository#agentStats()} — per-agent lead counts and pipeline value,
 * archived leads excluded. */
public interface LeadAgentStatsView {
    Long getAgentId();

    String getAgentUuid();

    String getAgentName();

    long getTotalLeads();

    long getConverted();

    BigDecimal getPipelineValue();
}
