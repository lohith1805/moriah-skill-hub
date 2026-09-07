package com.moriah.skillhub.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.learning.dto.AddLessonQuizQuestionRequest;
import com.moriah.skillhub.learning.dto.LessonQuizResultResponse;
import com.moriah.skillhub.learning.dto.SubmitLessonQuizRequest;
import com.moriah.skillhub.learning.entity.LessonProgress;
import com.moriah.skillhub.learning.entity.LessonProgressStatus;
import com.moriah.skillhub.learning.entity.LessonQuizAttempt;
import com.moriah.skillhub.learning.entity.LessonQuizQuestion;
import com.moriah.skillhub.learning.entity.VideoLesson;
import com.moriah.skillhub.learning.repository.LessonProgressRepository;
import com.moriah.skillhub.learning.repository.LessonQuizAttemptRepository;
import com.moriah.skillhub.learning.repository.LessonQuizQuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonQuizServiceTest {

    @Mock
    private LessonQuizQuestionRepository questionRepository;
    @Mock
    private LessonQuizAttemptRepository attemptRepository;
    @Mock
    private LessonProgressRepository progressRepository;
    @Mock
    private LessonService lessonService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private LessonQuizService service;

    private VideoLesson lesson(long id) {
        VideoLesson l = new VideoLesson();
        l.setId(id);
        l.setCreatedBy(9L);
        return l;
    }

    private LessonQuizQuestion q(long id, int correct) {
        LessonQuizQuestion q = new LessonQuizQuestion();
        q.setId(id);
        q.setLessonId(1L);
        q.setQuestionText("Q" + id);
        q.setOptions("[\"a\",\"b\",\"c\",\"d\"]");
        q.setCorrectIndex(correct);
        return q;
    }

    @Test
    void addQuestion_setsSortOrderToCurrentCount_andNeverReturnsTheKey() {
        when(lessonService.requireLesson(1L)).thenReturn(lesson(1L));
        when(questionRepository.countByLessonId(1L)).thenReturn(2L);
        when(questionRepository.save(any(LessonQuizQuestion.class))).thenAnswer(inv -> {
            LessonQuizQuestion x = inv.getArgument(0);
            x.setId(10L);
            return x;
        });

        var res = service.addQuestion(1L, new AddLessonQuizQuestionRequest(
                "What is 2+2?", List.of("3", "4", "5"), 1, "basic"), 9L);

        ArgumentCaptor<LessonQuizQuestion> cap = ArgumentCaptor.forClass(LessonQuizQuestion.class);
        verify(questionRepository).save(cap.capture());
        assertThat(cap.getValue().getSortOrder()).isEqualTo(2);
        assertThat(cap.getValue().getCorrectIndex()).isEqualTo(1);
        assertThat(res.options()).containsExactly("3", "4", "5");
        // response record has no correctIndex accessor — compile-time guarantee
    }

    @Test
    void addQuestionRequest_correctIndexOutOfRange_rejectedAtConstruction() {
        assertThatThrownBy(() -> new AddLessonQuizQuestionRequest("q", List.of("a", "b"), 5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submit_allCorrect_passesAndMarksLessonCompleted() {
        when(questionRepository.findByLessonIdOrderBySortOrderAscIdAsc(1L))
                .thenReturn(List.of(q(1L, 1), q(2L, 0), q(3L, 3)));
        when(attemptRepository.findByLessonIdAndUserId(1L, 50L)).thenReturn(Optional.empty());
        when(progressRepository.findByLessonIdAndUserId(1L, 50L)).thenReturn(Optional.empty());

        LessonQuizResultResponse r = service.submit(1L, new SubmitLessonQuizRequest(List.of(1, 0, 3)), 50L);

        assertThat(r.score()).isEqualTo(3);
        assertThat(r.total()).isEqualTo(3);
        assertThat(r.passed()).isTrue();

        ArgumentCaptor<LessonProgress> pcap = ArgumentCaptor.forClass(LessonProgress.class);
        verify(progressRepository).save(pcap.capture());
        assertThat(pcap.getValue().getStatus()).isEqualTo(LessonProgressStatus.COMPLETED);
        assertThat(pcap.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void submit_belowSixtyPercent_failsAndDoesNotTouchProgress() {
        when(questionRepository.findByLessonIdOrderBySortOrderAscIdAsc(1L))
                .thenReturn(List.of(q(1L, 1), q(2L, 1), q(3L, 1)));
        when(attemptRepository.findByLessonIdAndUserId(1L, 50L)).thenReturn(Optional.empty());

        LessonQuizResultResponse r = service.submit(1L, new SubmitLessonQuizRequest(List.of(1, 0, 0)), 50L);

        assertThat(r.score()).isEqualTo(1);
        assertThat(r.passed()).isFalse();
        verify(progressRepository, never()).save(any());
    }

    @Test
    void submit_shortAnswersList_countsMissingAsWrong() {
        when(questionRepository.findByLessonIdOrderBySortOrderAscIdAsc(1L))
                .thenReturn(List.of(q(1L, 1), q(2L, 2)));
        when(attemptRepository.findByLessonIdAndUserId(1L, 50L)).thenReturn(Optional.empty());

        LessonQuizResultResponse r = service.submit(1L, new SubmitLessonQuizRequest(List.of(1)), 50L);

        assertThat(r.score()).isEqualTo(1);
        assertThat(r.total()).isEqualTo(2);
        assertThat(r.passed()).isFalse(); // 1/2 = 50% < 60% pass mark
    }

    @Test
    void submit_reusesExistingAttemptRow() {
        LessonQuizAttempt existing = new LessonQuizAttempt();
        existing.setId(7L);
        existing.setLessonId(1L);
        existing.setUserId(50L);
        when(questionRepository.findByLessonIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(q(1L, 0)));
        when(attemptRepository.findByLessonIdAndUserId(1L, 50L)).thenReturn(Optional.of(existing));
        when(progressRepository.findByLessonIdAndUserId(1L, 50L)).thenReturn(Optional.empty());

        service.submit(1L, new SubmitLessonQuizRequest(List.of(0)), 50L);

        ArgumentCaptor<LessonQuizAttempt> cap = ArgumentCaptor.forClass(LessonQuizAttempt.class);
        verify(attemptRepository).save(cap.capture());
        assertThat(cap.getValue().getId()).isEqualTo(7L);
        assertThat(cap.getValue().getScore()).isEqualTo(1);
    }

    @Test
    void submit_noQuestions_throwsNotFound() {
        when(questionRepository.findByLessonIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(1L, new SubmitLessonQuizRequest(List.of()), 50L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
