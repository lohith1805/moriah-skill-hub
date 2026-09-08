package com.moriah.skillhub.submission.repository;

import com.moriah.skillhub.submission.entity.WeeklyReview;
import com.moriah.skillhub.submission.entity.WeeklyRating;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyReviewRepository extends JpaRepository<WeeklyReview, Long> {

    /** V7's {@code uq_weekly_reviews_user_week} guarantees at most one row — feature 17's
     * {@code REVIEW_FAILED} PIP rule reads through here too (build-plan.md feature 12: "This is
     * the data source for the REVIEW_FAILED PIP rule"). */
    Optional<WeeklyReview> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);

    /** {@code GET /api/v1/reviews/weekly?batchId=&weekStart=} — the ratings already filed for one
     * batch in one week, so the trainer's grid pre-fills instead of re-rating from scratch. */
    @EntityGraph(attributePaths = "user")
    List<WeeklyReview> findByBatchIdAndWeekStart(Long batchId, LocalDate weekStart);

    /** {@code PipService}'s clearance check (feature 17) — deliberately windowed to {@code since}
     * (the PIP record's {@code start_date}), unlike {@code student_metrics.unsatisfactory_reviews}
     * (lifetime-cumulative within the batch, feature 16's own decision). Using the cumulative
     * column here would make {@code CLEARED} permanently unreachable for any student who ever had
     * one unsatisfactory review, even years earlier and unrelated to this PIP — a `/review` finding
     * against this exact method before it existed. */
    boolean existsByUserIdAndWeekStartGreaterThanEqualAndRating(Long userId, LocalDate since, WeeklyRating rating);
}
