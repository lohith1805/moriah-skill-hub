package com.moriah.skillhub.assessment;

import com.moriah.skillhub.assessment.entity.AttemptStatus;
import com.moriah.skillhub.assessment.entity.QuestionType;
import com.moriah.skillhub.assessment.entity.Quiz;
import com.moriah.skillhub.assessment.entity.QuizAttempt;
import com.moriah.skillhub.assessment.entity.QuizQuestion;
import com.moriah.skillhub.assessment.repository.QuizAnswerRepository;
import com.moriah.skillhub.assessment.repository.QuizAttemptRepository;
import com.moriah.skillhub.assessment.repository.QuizQuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** build-plan.md feature 14: "Unsubmitted attempts swept to EXPIRED and scored on answers
 * given." Tests the {@code @Transactional} core directly on {@link QuizAttemptExpiryService} — a
 * separate bean from {@link QuizAttemptExpiryJob} precisely so that {@code @Transactional}
 * actually applies on the real {@code @Scheduled} trigger (see that class's Javadoc); this test
 * exercises the same method a unit test calling it on the job bean previously did, just relocated.
 * {@code GradingService} is real (no mocking needed, same reasoning {@code GradingServiceTest}
 * documents) so the arithmetic is genuinely exercised, not just delegation verified. */
@ExtendWith(MockitoExtension.class)
class QuizAttemptExpiryServiceTest {

    @Mock
    private QuizAttemptRepository quizAttemptRepository;
    @Mock
    private QuizQuestionRepository quizQuestionRepository;
    @Mock
    private QuizAnswerRepository quizAnswerRepository;

    private final GradingService gradingService = new GradingService(new com.fasterxml.jackson.databind.ObjectMapper());

    private QuizAttemptExpiryService service() {
        return new QuizAttemptExpiryService(quizAttemptRepository, quizQuestionRepository,
                quizAnswerRepository, gradingService);
    }

    private Quiz quiz(long id, int durationMinutes) {
        Quiz quiz = new Quiz();
        quiz.setId(id);
        quiz.setDurationMinutes(durationMinutes);
        quiz.setPassPercentage(60);
        return quiz;
    }

    private QuizAttempt attempt(long id, Quiz quiz, Instant startedAt) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(id);
        attempt.setQuiz(quiz);
        attempt.setAttemptNumber(1);
        attempt.setStartedAt(startedAt);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        return attempt;
    }

    @Test
    void expire_pastDurationWindow_marksExpiredAndScoresZero() {
        Quiz quiz = quiz(1L, 30);
        QuizAttempt attempt = attempt(10L, quiz, Instant.now().minus(1, ChronoUnit.HOURS));
        QuizQuestion mcq = new QuizQuestion();
        mcq.setId(100L);
        mcq.setQuiz(quiz);
        mcq.setQuestionType(QuestionType.MCQ);
        mcq.setMarks(10);
        mcq.setCorrectAnswer("[1]");

        when(quizAttemptRepository.findByStatus(AttemptStatus.IN_PROGRESS)).thenReturn(List.of(attempt));
        when(quizQuestionRepository.findByQuizIdIn(any())).thenReturn(List.of(mcq));

        int count = service().expire();

        assertThat(count).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(attempt.getSubmittedAt()).isNotNull();
        assertThat(attempt.getPercentage()).isEqualByComparingTo("0.00");
        assertThat(attempt.getPassed()).isFalse();
        verify(quizAttemptRepository).saveAll(List.of(attempt));
    }

    @Test
    void expire_stillWithinDurationWindow_leftAlone() {
        Quiz quiz = quiz(1L, 30);
        QuizAttempt attempt = attempt(10L, quiz, Instant.now().minus(5, ChronoUnit.MINUTES));
        when(quizAttemptRepository.findByStatus(AttemptStatus.IN_PROGRESS)).thenReturn(List.of(attempt));

        int count = service().expire();

        assertThat(count).isZero();
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        verify(quizQuestionRepository, never()).findByQuizIdIn(any());
        verify(quizAttemptRepository, never()).saveAll(any());
    }

    @Test
    void expire_noInProgressAttempts_returnsZero() {
        when(quizAttemptRepository.findByStatus(AttemptStatus.IN_PROGRESS)).thenReturn(List.of());

        int count = service().expire();

        assertThat(count).isZero();
        verify(quizAnswerRepository, never()).saveAll(any());
    }

    @Test
    void expire_mixOfExpiredAndActive_onlyExpiredAreTouched() {
        Quiz quiz = quiz(1L, 30);
        QuizAttempt expired = attempt(10L, quiz, Instant.now().minus(1, ChronoUnit.HOURS));
        QuizAttempt active = attempt(11L, quiz, Instant.now().minus(2, ChronoUnit.MINUTES));
        when(quizAttemptRepository.findByStatus(AttemptStatus.IN_PROGRESS)).thenReturn(List.of(expired, active));
        when(quizQuestionRepository.findByQuizIdIn(any())).thenReturn(List.of());

        int count = service().expire();

        assertThat(count).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(active.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }
}
