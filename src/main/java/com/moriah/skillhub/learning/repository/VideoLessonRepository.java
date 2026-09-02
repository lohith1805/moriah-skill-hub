package com.moriah.skillhub.learning.repository;

import com.moriah.skillhub.learning.entity.VideoLesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VideoLessonRepository extends JpaRepository<VideoLesson, Long> {

    /**
     * The lesson catalogue (gap B1.4). {@code publishedOnly} true is the learner view;
     * curators pass false. {@code module} is an optional exact-match filter. Ordering is by
     * module then {@code sortOrder} so the FE can render module sections directly — the
     * {@code Pageable}'s own sort still applies on top when the caller supplies one.
     */
    @Query("""
            SELECT l FROM VideoLesson l
             WHERE (:publishedOnly = false OR l.published = true)
               AND (:module IS NULL OR l.moduleName = :module)
            """)
    Page<VideoLesson> search(@Param("publishedOnly") boolean publishedOnly,
                             @Param("module") String module,
                             Pageable pageable);

    /** {@code GET /api/v1/lessons/modules} — distinct published module names with a lesson count. */
    @Query("""
            SELECT l.moduleName AS moduleName, COUNT(l) AS lessonCount
              FROM VideoLesson l
             WHERE l.published = true
             GROUP BY l.moduleName
             ORDER BY l.moduleName
            """)
    List<ModuleSummaryProjection> summariseModules();

    interface ModuleSummaryProjection {
        String getModuleName();

        long getLessonCount();
    }
}
