package com.moriah.skillhub.assessment;

import com.moriah.skillhub.assessment.dto.SubmitAnswerRequest;
import com.moriah.skillhub.assessment.entity.AttemptStatus;
import com.moriah.skillhub.assessment.entity.Quiz;
import com.moriah.skillhub.assessment.entity.QuizAnswer;
import com.moriah.skillhub.assessment.entity.QuizAttempt;
import com.moriah.skillhub.assessment.entity.QuizQuestion;
import com.moriah.skillhub.assessment.repository.QuizAnswerRepository;
import com.moriah.skillhub.assessment.repository.QuizAttemptRepository;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A separate bean, {@code REQUIRES_NEW}, deliberately — same reasoning {@code QuizAttemptWriter}
 * documents. Unlike starting an attempt, submitting one has no single unique-key column to race
 * on; the race surfaces as a {@code quiz_answers} unique {@code (attempt_id, question_id)}
 * violation when two concurrent submits (a double-click, or a client retry after a slow response)
 * both read {@code IN_PROGRESS} before either commits and both try to grade-and-insert. Doing the
 * grade-and-save as one {@code REQUIRES_NEW} unit means the loser's whole attempt rolls back
 * atomically instead of leaving a half-graded attempt stuck mid-{@code IN_PROGRESS}, and {@link
 * QuizService#submit} can recover by reading back the winner's already-committed, already-graded
 * row instead of surfacing a raw {@code 500}.
 * <p>
 * Both methods return the attempt <em>and</em> its answers together, not just the attempt — {@code
 * QuizService#submit}'s own transaction took its MySQL REPEATABLE READ snapshot before this
 * bean's transaction ever ran (at its very first read, {@code requireAttempt}), so a plain {@code
 * quizAnswerRepository.findByAttemptId} issued from the caller afterward would silently see zero
 * rows — confirmed the hard way, same class of bug {@code QuizAttemptWriter.findExisting}'s own
 * Javadoc documents, just hitting the answers table instead of the attempts table this time.
 */
@Service
@RequiredArgsConstructor
class QuizSubmissionWriter {

    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final GradingService gradingService;

    record Result(QuizAttempt attempt, List<QuizAnswer> answers) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Result trySubmit(Long attemptId, List<QuizQuestion> questions, Map<Long, SubmitAnswerRequest> byQuestionId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND, attemptId));
        Quiz quiz = attempt.getQuiz();

        GradingResult result = gradingService.grade(questions, byQuestionId);
        for (QuizAnswer answer : result.answers()) {
            answer.setAttempt(attempt);
        }
        List<QuizAnswer> savedAnswers = quizAnswerRepository.saveAll(result.answers());

        gradingService.applyResult(attempt, result, quiz);
        attempt.setSubmittedAt(Instant.now());
        attempt.setStatus(result.hasCodeQuestions() ? AttemptStatus.PENDING_MANUAL_GRADING : AttemptStatus.SUBMITTED);
        QuizAttempt savedAttempt = quizAttemptRepository.saveAndFlush(attempt);

        return new Result(savedAttempt, savedAnswers);
    }

    /** Also {@code REQUIRES_NEW} — same "fresh snapshot after the winner's commit" reasoning
     * {@code QuizAttemptWriter.findExisting} documents. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Result findExisting(Long attemptId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND, attemptId));
        return new Result(attempt, quizAnswerRepository.findByAttemptId(attemptId));
    }
}
