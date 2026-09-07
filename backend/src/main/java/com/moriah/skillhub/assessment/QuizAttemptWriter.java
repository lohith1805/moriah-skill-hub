package com.moriah.skillhub.assessment;

import com.moriah.skillhub.assessment.entity.QuizAttempt;
import com.moriah.skillhub.assessment.repository.QuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * A separate bean, {@code REQUIRES_NEW}, deliberately — same reasoning {@code
 * TaskSubmissionWriter}/{@code AttendanceWriter} already document: catching a duplicate-key (or
 * deadlock) failure from a Hibernate-backed {@code save()} and continuing still leaves the
 * *session* poisoned the instant a flush fails. Starting an attempt has the same unique
 * {@code (quiz_id, user_id, attempt_number)} race shape {@code TaskSubmission}/{@code Attendance}
 * already solved.
 */
@Service
@RequiredArgsConstructor
class QuizAttemptWriter {

    private final QuizAttemptRepository quizAttemptRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    QuizAttempt tryInsert(QuizAttempt attempt) {
        return quizAttemptRepository.saveAndFlush(attempt);
    }

    /** Also {@code REQUIRES_NEW} — MySQL/InnoDB's REPEATABLE READ snapshot is fixed at the outer
     * transaction's first read; a fresh transaction here gets a fresh snapshot taken after the
     * winner's commit (confirmed the hard way, feature 12/13). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<QuizAttempt> findExisting(Long quizId, Long userId, int attemptNumber) {
        return quizAttemptRepository.findByQuizIdAndUserIdAndAttemptNumber(quizId, userId, attemptNumber);
    }
}
