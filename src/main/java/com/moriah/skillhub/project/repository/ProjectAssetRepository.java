package com.moriah.skillhub.project.repository;

import com.moriah.skillhub.project.entity.ProjectAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProjectAssetRepository extends JpaRepository<ProjectAsset, Long> {

    List<ProjectAsset> findByProjectIdOrderBySortOrderAsc(Long projectId);

    /** One flat query for every asset across a whole page of projects (code-standards.md "N+1
     * Prevention") — {@code ProjectService#list} groups the result by {@code projectId} in
     * memory rather than calling {@link #findByProjectIdOrderBySortOrderAsc} once per project. */
    List<ProjectAsset> findByProjectIdInOrderBySortOrderAsc(Collection<Long> projectIds);
}
