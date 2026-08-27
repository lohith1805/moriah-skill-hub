package com.moriah.skillhub.submission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.sprint.TaskService;
import com.moriah.skillhub.sprint.entity.TaskStatus;
import com.moriah.skillhub.submission.dto.CreateReviewRequest;
import com.moriah.skillhub.submission.dto.InlineComment;
import com.moriah.skillhub.submission.dto.ReviewResponse;
import com.moriah.skillhub.submission.entity.CodeReview;
import com.moriah.skillhub.submission.entity.ReviewVerdict;
import com.moriah.skillhub.submission.entity.SubmissionStatus;
import com.moriah.skillhub.submission.entity.TaskSubmission;
import com.moriah.skillhub.submission.repository.CodeReviewRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * architecture.md's {@code submission/} package diagram names this and {@link
 * WeeklyReviewService} as two separate services (not one combined "ReviewService"), both behind
 * one {@code ReviewController}.
 * <p>
 * {@code CHANGES_REQUESTED -> TaskStatus.IN_PROGRESS}, not {@code REJECTED} — see {@code
 * TaskService.ALLOWED_TRANSITIONS}' Javadoc: {@code REJECTED} is terminal, and {@code
 * task_submissions}' own {@code attempt_number} column presupposes resubmission is expected
 * after a review verdict, not a dead end. {@code REJECTED} stays reachable only via a PM's
 * direct {@code PUT /tasks/{id}} call, never through this verdict path.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodeReviewService {

    private final CodeReviewRepository codeReviewRepository;
    private final UserRepository userRepository;
    private final SubmissionService submissionService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReviewResponse create(Long callerUserId, CreateReviewRequest request) {
        TaskSubmission submission = submissionService.requireSubmission(request.submissionId());
        User reviewer = requireUser(callerUserId);

        TaskStatus outcome = request.verdict() == ReviewVerdict.APPROVED ? TaskStatus.COMPLETED : TaskStatus.IN_PROGRESS;
        taskService.completeReview(submission.getTaskId(), callerUserId, outcome);

        submission.setStatus(SubmissionStatus.valueOf(request.verdict().name()));

        CodeReview review = new CodeReview();
        review.setSubmission(submission);
        review.setReviewer(reviewer);
        review.setScore(request.score());
        review.setVerdict(request.verdict());
        review.setComments(request.comments());
        review.setInlineComments(toJson(request.inlineComments()));
        review.setReviewedAt(Instant.now());
        codeReviewRepository.save(review);

        return toResponse(review);
    }

    private String toJson(List<InlineComment> inlineComments) {
        if (inlineComments == null || inlineComments.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(inlineComments);
        } catch (JsonProcessingException e) {
            log.warn("[submission/review] failed to serialize inline comments, storing null", e);
            return null;
        }
    }

    private List<InlineComment> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<InlineComment>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("[submission/review] failed to parse stored inline comments, treating as empty", e);
            return List.of();
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
    }

    private ReviewResponse toResponse(CodeReview review) {
        User reviewer = review.getReviewer();
        return new ReviewResponse(
                review.getId(),
                review.getSubmission().getId(),
                reviewer.getUuid(),
                reviewer.getFullName(),
                review.getScore(),
                review.getVerdict(),
                review.getComments(),
                fromJson(review.getInlineComments()),
                review.getReviewedAt());
    }
}
