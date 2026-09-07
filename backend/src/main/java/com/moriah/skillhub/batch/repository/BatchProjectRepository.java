package com.moriah.skillhub.batch.repository;

import com.moriah.skillhub.batch.entity.BatchProject;
import com.moriah.skillhub.batch.entity.BatchProjectId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BatchProjectRepository extends JpaRepository<BatchProject, BatchProjectId> {

    @Query("SELECT bp.id.projectId FROM BatchProject bp WHERE bp.id.batchId = :batchId")
    List<Long> findProjectIdsByBatchId(@Param("batchId") Long batchId);

    /** {@code BatchService#assignProjects}'s wholesale replace — same delete-then-insert idiom
     * {@code LeadCampaignService#update} uses for {@code lead_campaign_recipients}. */
    void deleteByIdBatchId(Long batchId);
}
