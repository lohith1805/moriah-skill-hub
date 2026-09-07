package com.moriah.skillhub.project;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.project.dto.ChallengeSubmissionResponse;
import com.moriah.skillhub.project.dto.ReviewChallengeSubmissionRequest;
import com.moriah.skillhub.project.dto.SubmitChallengeRequest;
import com.moriah.skillhub.project.entity.BugChallenge;
import com.moriah.skillhub.project.entity.ChallengeSubmission;
import com.moriah.skillhub.project.entity.ChallengeSubmissionStatus;
import com.moriah.skillhub.project.repository.BugChallengeRepository;
import com.moriah.skillhub.project.repository.ChallengeSubmissionRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Bug-fix challenge submissions (frontend gap — the student page could only flip a localStorage
 * "fixed" flag). A student is shown the broken code + the expected behaviour, rewrites the fix,
 * and submits it here; a developer / trainer then reads every submission and can leave feedback
 * and a score. Kept separate from the already-large {@link ProjectService} — this is a leaf
 * concern with its own table and its own controller.
 */
@Service
@RequiredArgsConstructor
public class ChallengeSubmissionService {

    private final ChallengeSubmissionRepository submissionRepository;
    private final BugChallengeRepository bugChallengeRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /** {@code POST /api/v1/challenges/{challengeId}/submissions} (STUDENT). A resubmission is a
     * new row — history is kept. */
    @Transactional
    public ChallengeSubmissionResponse submit(Long callerUserId, Long challengeId, SubmitChallengeRequest request) {
        BugChallenge challenge = bugChallengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BUG_CHALLENGE_NOT_FOUND, challengeId));
        User student = requireUser(callerUserId);

        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setChallenge(challenge);
        submission.setStudent(student);
        submission.setSolutionCode(request.solutionCode());
        submission.setNotes(blankToNull(request.notes()));
        submission.setStatus(ChallengeSubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(Instant.now());
        submissionRepository.save(submission);

        auditLogService.record(callerUserId, "CHALLENGE_SUBMISSION_CREATED", "ChallengeSubmission",
                submission.getId(), null, submission);
        return toResponse(submission, true);
    }

    /** {@code GET /api/v1/challenges/submissions/me} (STUDENT) — the caller's own submissions. */
    @Transactional(readOnly = true)
    public PageResponse<ChallengeSubmissionResponse> mySubmissions(Long callerUserId, Pageable pageable) {
        return PageResponse.from(submissionRepository.findMine(callerUserId, pageable)
                .map(s -> toResponse(s, true)));
    }

    /** {@code GET /api/v1/challenges/{challengeId}/submissions} (DEVELOPER / TRAINER_PM / ADMIN). */
    @Transactional(readOnly = true)
    public PageResponse<ChallengeSubmissionResponse> submissionsForChallenge(Long challengeId, Pageable pageable) {
        if (!bugChallengeRepository.existsById(challengeId)) {
            throw new ResourceNotFoundException(ErrorCode.BUG_CHALLENGE_NOT_FOUND, challengeId);
        }
        return PageResponse.from(submissionRepository.findForChallenge(challengeId, pageable)
                .map(s -> toResponse(s, true)));
    }

    /** {@code PUT /api/v1/challenges/submissions/{id}/review} (DEVELOPER / TRAINER_PM / ADMIN). */
    @Transactional
    public ChallengeSubmissionResponse review(Long callerUserId, Long submissionId,
                                              ReviewChallengeSubmissionRequest request) {
        ChallengeSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHALLENGE_SUBMISSION_NOT_FOUND, submissionId));
        if (request.status() == ChallengeSubmissionStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "A review must set status to ACCEPTED or NEEDS_WORK.");
        }
        submission.setStatus(request.status());
        submission.setReviewerFeedback(blankToNull(request.feedback()));
        submission.setScore(request.score());
        submission.setReviewer(requireUser(callerUserId));
        submission.setReviewedAt(Instant.now());
        submissionRepository.save(submission);

        auditLogService.record(callerUserId, "CHALLENGE_SUBMISSION_REVIEWED", "ChallengeSubmission",
                submission.getId(), null, submission);
        return toResponse(submission, true);
    }

    private ChallengeSubmissionResponse toResponse(ChallengeSubmission s, boolean includeCode) {
        BugChallenge c = s.getChallenge();
        return new ChallengeSubmissionResponse(
                s.getId(),
                c.getId(),
                c.getTitle(),
                c.getProject().getId(),
                c.getProject().getTitle(),
                s.getStudent().getUuid(),
                s.getStudent().getFullName(),
                includeCode ? s.getSolutionCode() : null,
                s.getNotes(),
                s.getStatus(),
                s.getReviewerFeedback(),
                s.getScore(),
                s.getSubmittedAt(),
                s.getReviewedAt());
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
