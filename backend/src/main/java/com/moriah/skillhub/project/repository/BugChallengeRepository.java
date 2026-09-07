package com.moriah.skillhub.project.repository;

import com.moriah.skillhub.project.entity.BugChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BugChallengeRepository extends JpaRepository<BugChallenge, Long> {

    List<BugChallenge> findByProjectId(Long projectId);

    /** Same "one flat query, group in memory" reasoning as {@code
     * ProjectAssetRepository#findByProjectIdInOrderBySortOrderAsc}. */
    List<BugChallenge> findByProjectIdIn(Collection<Long> projectIds);
}
