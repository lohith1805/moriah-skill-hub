package com.moriah.skillhub.pip.repository;

import com.moriah.skillhub.pip.entity.PipMilestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipMilestoneRepository extends JpaRepository<PipMilestone, Long> {

    List<PipMilestone> findByPipRecordId(Long pipRecordId);

    /** {@code PipService#list}'s bulk read (feature 17 `/review` finding) — one flat query for
     * every milestone across a whole page of records, not one {@link #findByPipRecordId} call per
     * record (code-standards.md "N+1 Prevention"). */
    List<PipMilestone> findByPipRecordIdIn(List<Long> pipRecordIds);
}
