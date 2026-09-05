package com.moriah.skillhub.project.repository;

import com.moriah.skillhub.project.dto.CompletedProjectProjection;
import com.moriah.skillhub.project.entity.Project;
import com.moriah.skillhub.project.entity.ProjectDifficulty;
import com.moriah.skillhub.project.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** Backs {@code GET /api/v1/projects?difficulty=&domain=&status=} — every filter optional.
     * {@code status} is never passed through from the raw query param for a non-privileged caller
     * (see {@code ProjectService#list}'s Javadoc); this query itself just applies whatever the
     * service decided. No {@code @EntityGraph}: {@code ProjectResponse} reads only {@code
     * createdBy.getUuid()}/{@code .getFullName()}, both non-null fields on an eagerly-needed
     * association — see the {@code @EntityGraph} below instead, applied because those two fields
     * (unlike a bare id) do require the join. */
    @EntityGraph(attributePaths = "createdBy")
    @Query("""
            SELECT p FROM Project p
             WHERE (:difficulty IS NULL OR p.difficulty = :difficulty)
               AND (:domain IS NULL OR p.domain = :domain)
               AND (:status IS NULL OR p.status = :status)
               AND (:track IS NULL OR p.track = :track)
             ORDER BY p.createdAt DESC
            """)
    Page<Project> search(@Param("difficulty") ProjectDifficulty difficulty, @Param("domain") String domain,
            @Param("status") ProjectStatus status, @Param("track") String track, Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    Optional<Project> findById(Long id);

    /** Slug-collision probe for {@code ProjectService}'s slugify-then-disambiguate logic — a
     * single indexed lookup per candidate slug, not a table scan. */
    boolean existsBySlug(String slug);

    /** {@code ProjectService#findTitlesAndSlugs}'s backing read — the Open Stub cleared this
     * feature: {@code UserService#getPortfolio}'s {@code completedProjects}. A projection, not
     * full entities — the portfolio needs title/slug only, never {@code techStack}/{@code status}/
     * {@code createdBy}. */
    @Query("""
            SELECT new com.moriah.skillhub.project.dto.CompletedProjectProjection(p.title, p.slug)
            FROM Project p WHERE p.id IN :projectIds
            """)
    List<CompletedProjectProjection> findTitlesAndSlugsByIdIn(@Param("projectIds") List<Long> projectIds);
}
