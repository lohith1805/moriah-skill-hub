package com.moriah.skillhub.learning.repository;

import com.moriah.skillhub.learning.entity.LessonProgress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByLessonIdAndUserId(Long lessonId, Long userId);

    /** Batch-load one page's progress for the caller — no N+1 when rendering the lesson list. */
    List<LessonProgress> findByUserIdAndLessonIdIn(Long userId, List<Long> lessonIds);

    /** {@code GET /api/v1/lessons/me/progress} — the caller's started lessons, paginated. */
    Page<LessonProgress> findByUserId(Long userId, Pageable pageable);
}
