package com.moriah.skillhub.submission;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.submission.dto.CreateWeeklyReviewRequest;
import com.moriah.skillhub.submission.dto.WeeklyReviewResponse;
import com.moriah.skillhub.submission.entity.WeeklyRating;
import com.moriah.skillhub.submission.entity.WeeklyReview;
import com.moriah.skillhub.submission.repository.WeeklyReviewRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** build-plan.md feature 12: "writes a weekly_reviews row ... the data source for the
 * REVIEW_FAILED PIP rule — the rule is unimplementable without it." {@code BatchRepository} is
 * injected directly — {@code Batch} is the established shared-kernel type (feature 11), same
 * pattern {@code SprintService} already uses. */
@Service
@RequiredArgsConstructor
public class WeeklyReviewService {

    private final WeeklyReviewRepository weeklyReviewRepository;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final UserRepository userRepository;

    /** Upserts by {@code (user, weekStart)} — see {@link CreateWeeklyReviewRequest}'s Javadoc
     * for why: no dedicated {@code PUT} endpoint exists for a PM correcting a rating, and
     * build-plan.md's own endpoint list only shows {@code POST}. */
    @Transactional
    public WeeklyReviewResponse create(Long callerUserId, CreateWeeklyReviewRequest request) {
        User student = requireUserByUuid(request.userUuid());
        User reviewer = requireUserById(callerUserId);
        Batch batch = requireBatch(request.batchId());
        // Missing until /review caught it: every other feature-12/11 write scoped to a batch
        // (SprintService.create, TaskService.completeReview) calls this; a rating that feeds the
        // REVIEW_FAILED PIP rule (feature 17) is exactly the kind of "hard to reverse" write
        // AGENTS.md singles out for care, so a PM rating a student outside their own batch is a
        // real gap, not a hypothetical one.
        batchService.requireOwnerOrAdmin(callerUserId, batch);

        WeeklyReview review = weeklyReviewRepository.findByUserIdAndWeekStart(student.getId(), request.weekStart())
                .orElseGet(WeeklyReview::new);

        review.setBatch(batch);
        review.setUser(student);
        review.setSprintId(request.sprintId());
        review.setWeekStart(request.weekStart());
        review.setRating(request.rating());
        review.setNotes(request.notes());
        review.setReviewedBy(reviewer);
        weeklyReviewRepository.save(review);

        return toResponse(review);
    }

    /** {@code GET /api/v1/reviews/weekly?batchId=&weekStart=} — every rating filed for one batch
     * in one week, for the trainer's grid. Ownership: a PM only reads their own batch's ratings. */
    @Transactional(readOnly = true)
    public List<WeeklyReviewResponse> list(Long callerUserId, Long batchId, LocalDate weekStart) {
        Batch batch = requireBatch(batchId);
        batchService.requireOwnerOrAdmin(callerUserId, batch);
        return weeklyReviewRepository.findByBatchIdAndWeekStart(batchId, weekStart).stream()
                .map(this::toResponse)
                .toList();
    }

    /** {@code PipService}'s clearance check (feature 17) — "no unsatisfactory review since the
     * PIP started," not "no unsatisfactory review ever" (see {@link WeeklyReviewRepository}'s own
     * Javadoc for why the cumulative {@code student_metrics} column is the wrong source here). */
    @Transactional(readOnly = true)
    public boolean hasUnsatisfactoryReviewSince(Long userId, LocalDate since) {
        return weeklyReviewRepository.existsByUserIdAndWeekStartGreaterThanEqualAndRating(
                userId, since, WeeklyRating.UNSATISFACTORY);
    }

    private User requireUserByUuid(String uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, uuid));
    }

    private User requireUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
    }

    private Batch requireBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, batchId));
    }

    private WeeklyReviewResponse toResponse(WeeklyReview review) {
        User student = review.getUser();
        return new WeeklyReviewResponse(
                review.getId(),
                student.getUuid(),
                student.getFullName(),
                review.getBatch().getId(),
                review.getSprintId(),
                review.getWeekStart(),
                review.getRating(),
                review.getNotes(),
                review.getReviewedBy().getUuid(),
                review.getReviewedAt());
    }
}
