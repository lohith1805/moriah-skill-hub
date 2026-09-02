package com.moriah.skillhub.resource.repository;

import com.moriah.skillhub.resource.entity.LearningResource;
import com.moriah.skillhub.resource.entity.ResourceCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {

    /**
     * The library feed (gap B1.6). {@code activeOnly} true limits to {@code is_active = true}
     * (the browsing view); staff management screens pass false to see everything. {@code
     * category} and {@code search} are optional — a {@code null} drops the predicate, same
     * nullable-parameter pattern as {@code UserRepository.search}. {@code search} matches title
     * or description, case-insensitively.
     */
    @Query("""
            SELECT r FROM LearningResource r
             WHERE (:activeOnly = false OR r.active = true)
               AND (:category IS NULL OR r.category = :category)
               AND (:search IS NULL
                    OR LOWER(r.title) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<LearningResource> search(@Param("activeOnly") boolean activeOnly,
                                  @Param("category") ResourceCategory category,
                                  @Param("search") String search,
                                  Pageable pageable);
}
