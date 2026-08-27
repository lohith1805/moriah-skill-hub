package com.moriah.skillhub.assessment;

import com.moriah.skillhub.assessment.entity.AttemptStatus;
import com.moriah.skillhub.assessment.entity.Quiz;
import com.moriah.skillhub.assessment.entity.QuizAnswer;
import com.moriah.skillhub.assessment.entity.QuizAttempt;
import com.moriah.skillhub.assessment.entity.QuizQuestion;
import com.moriah.skillhub.assessment.repository.QuizAnswerRepository;
import com.moriah.skillhub.assessment.repository.QuizAttemptRepository;
import com.moriah.skillhub.assessment.repository.QuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A separate bean from {@link QuizAttemptExpiryJob}, deliberately — code-standards.md
 * "Transactions": "Never rely on {@code @Transactional} on a private or self-invoked method — it
 * does nothing." {@code QuizAttemptExpiryJob.expireScheduled()} is itself a {@code @Scheduled}
 * method invoked by Spring's scheduler directly on the target bean, not through the transactional
 * proxy; a same-class call from there to {@link #expire} would be exactly that self-invocation,
 * silently running each repository write in its own auto-committing mini-transaction instead of
 * the one atomic unit this method's contract promises. Calling {@link #expire} on this separate
 * bean instead goes through Spring's proxy like any other cross-bean call, so {@code
 * @Transactional} actually applies. Same fix, same reasoning as {@code
 * AttendanceFinalisationService} — see that class's Javadoc and progress-tracker.md.
 */
@Service
@RequiredArgsConstructor
public class QuizAttemptExpiryService {

    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final GradingService gradingService;

    /** One flat query for every {@code IN_PROGRESS} attempt, expiry computed in memory against
     * each one's own {@code quiz.durationMinutes} (varies per quiz, so it can't be pushed into the
     * {@code WHERE} clause as a single cutoff) — then one flat query for every question across
     * every expiring attempt's quiz, grouped in memory (code-standards.md "N+1 Prevention": no
     * repository call in the per-item loop). */
    @Transactional
    public int expire() {
        Instant now = Instant.now();
        List<QuizAttempt> inProgress = quizAttemptRepository.findByStatus(AttemptStatus.IN_PROGRESS);
        List<QuizAttempt> expired = inProgress.stream()
                .filter(a -> a.getStartedAt().plus(Duration.ofMinutes(a.getQuiz().getDurationMinutes())).isBefore(now))
                .toList();
        if (expired.isEmpty()) {
            return 0;
        }

        Set<Long> quizIds = expired.stream().map(a -> a.getQuiz().getId()).collect(Collectors.toSet());
        Map<Long, List<QuizQuestion>> questionsByQuiz = quizQuestionRepository.findByQuizIdIn(List.copyOf(quizIds)).stream()
                .collect(Collectors.groupingBy(q -> q.getQuiz().getId()));

        List<QuizAnswer> newAnswers = new ArrayList<>();
        for (QuizAttempt attempt : expired) {
            Quiz quiz = attempt.getQuiz();
            List<QuizQuestion> questions = questionsByQuiz.getOrDefault(quiz.getId(), List.of());

            GradingResult result = gradingService.grade(questions, Map.of());
            for (QuizAnswer answer : result.answers()) {
                answer.setAttempt(attempt);
            }
            newAnswers.addAll(result.answers());

            gradingService.applyResult(attempt, result, quiz);
            attempt.setSubmittedAt(now);
            attempt.setStatus(AttemptStatus.EXPIRED);
        }

        quizAnswerRepository.saveAll(newAnswers);
        quizAttemptRepository.saveAll(expired);

        return expired.size();
    }
}
