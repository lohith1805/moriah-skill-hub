package com.moriah.skillhub.user.repository;

import com.moriah.skillhub.user.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(Long userId);

    Optional<UserProfile> findByPortfolioSlug(String portfolioSlug);

    boolean existsByPortfolioSlug(String portfolioSlug);

    /**
     * Talent pool browse (gap B1.9) — profiles that are complete, have a published portfolio
     * slug, <b>and belong to a student who has GRADUATED from at least one batch</b> (the pool
     * is graduates only — a current or dropped-out student never appears, however complete their
     * profile). {@code search} matches name or current title; {@code skill} is a substring match
     * against the stored {@code skills} JSON text (crude but adequate — nothing else queries
     * into that column). Both optional; {@code null} drops the predicate. {@code user} eager so
     * the candidate response builds without a lazy round trip per row.
     * <p>
     * The graduation check is an {@code EXISTS} subquery over {@code BatchStudent} rather than a
     * caller-supplied id set — keeps this one paginated query authoritative and avoids fetching
     * every graduate's id up front. {@code BatchStudent} is referenced by entity name only (no
     * Java import), the same soft cross-module read {@code placement}/{@code talent} already do
     * against {@code BatchStudentRepository}.
     */
    @Query("""
            SELECT p FROM UserProfile p
             WHERE p.complete = true AND p.portfolioSlug IS NOT NULL
               AND EXISTS (SELECT 1 FROM BatchStudent bs
                            WHERE bs.user.id = p.user.id AND bs.status = 'GRADUATED')
               AND (:search IS NULL
                    OR LOWER(p.user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(p.currentTitle) LIKE LOWER(CONCAT('%', :search, '%')))
               AND (:skill IS NULL OR LOWER(p.skills) LIKE LOWER(CONCAT('%', :skill, '%')))
            """)
    @EntityGraph(attributePaths = "user")
    Page<UserProfile> searchTalentPool(@Param("search") String search,
                                       @Param("skill") String skill,
                                       Pageable pageable);
}
