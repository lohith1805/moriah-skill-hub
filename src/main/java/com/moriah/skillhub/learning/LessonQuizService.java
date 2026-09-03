package com.moriah.skillhub.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.learning.dto.AddLessonQuizQuestionRequest;
import com.moriah.skillhub.learning.dto.LessonQuizQuestionResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Per-lesson quiz (gap B1.4, remaining half). Curators (DEVELOPER/TRAINER_PM/ADMIN) author MCQs
 * on a lesson; a learner submits answers and, on >= 60%, the lesson's {@link LessonProgress} is
 * upserted to {@code COMPLETED}. {@code correctIndex} is never serialized. Reuses
 * {@link LessonService}'s {@code requireLesson}/{@code requireCreatorOrAdmin} (same package).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LessonQuizService {

    private static final int PASS_MARK_PERCENT = 60;

    private final LessonQuizQuestionRepository questionRepository;
    private final LessonQuizAttemptRepository attemptRepository;
    private final LessonProgressRepository progressRepository;
    private final LessonService lessonService;
    private final ObjectMapper objectMapper;

    @Transactional
    public LessonQuizQuestionResponse addQuestion(Long lessonId, AddLessonQuizQuestionRequest request, Long callerUserId) {
        VideoLesson lesson = lessonService.requireLesson(lessonId);
        lessonService.requireCreatorOrAdmin(lesson, callerUserId);

        LessonQuizQuestion q = new LessonQuizQuestion();
        q.setLessonId(lessonId);
        q.setQuestionText(request.questionText());
        q.setOptions(toJson(request.options()));
        q.setCorrectIndex(request.correctIndex());
        q.setExplanation(blankToNull(request.explanation()));
        q.setSortOrder((int) questionRepository.countByLessonId(lessonId));
        q.setCreatedBy(callerUserId);
        questionRepository.save(q);
        return toResponse(q);
    }

    @Transactional(readOnly = true)
    public List<LessonQuizQuestionResponse> listQuestions(Long lessonId) {
        lessonService.requireLesson(lessonId);
        return questionRepository.findByLessonIdOrderBySortOrderAscIdAsc(lessonId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void removeQuestion(Long lessonId, Long questionId, Long callerUserId) {
        VideoLesson lesson = lessonService.requireLesson(lessonId);
        lessonService.requireCreatorOrAdmin(lesson, callerUserId);
        LessonQuizQuestion q = questionRepository.findByIdAndLessonId(questionId, lessonId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, questionId));
        questionRepository.delete(q);
    }

    /**
     * Grade a submission. A missing / out-of-range answer is wrong. On >= 60% the lesson's
     * progress row is upserted to {@code COMPLETED} (stamping {@code completedAt} once). The
     * latest attempt per (lesson, user) is kept — a re-submit overwrites.
     */
    @Transactional
    public LessonQuizResultResponse submit(Long lessonId, SubmitLessonQuizRequest request, Long callerUserId) {
        lessonService.requireLesson(lessonId);
        List<LessonQuizQuestion> questions = questionRepository.findByLessonIdOrderBySortOrderAscIdAsc(lessonId);
        if (questions.isEmpty()) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "quiz for lesson " + lessonId);
        }

        List<Integer> answers = request.answers();
        int score = 0;
        for (int i = 0; i < questions.size(); i++) {
            Integer given = i < answers.size() ? answers.get(i) : null;
            if (given != null && given.equals(questions.get(i).getCorrectIndex())) {
                score++;
            }
        }
        int total = questions.size();
        boolean passed = score * 100 >= total * PASS_MARK_PERCENT;
        Instant now = Instant.now();

        LessonQuizAttempt attempt = attemptRepository.findByLessonIdAndUserId(lessonId, callerUserId)
                .orElseGet(() -> {
                    LessonQuizAttempt fresh = new LessonQuizAttempt();
                    fresh.setLessonId(lessonId);
                    fresh.setUserId(callerUserId);
                    return fresh;
                });
        attempt.setScore(score);
        attempt.setTotal(total);
        attempt.setPassed(passed);
        attempt.setSubmittedAt(now);
        attemptRepository.save(attempt);

        if (passed) {
            LessonProgress progress = progressRepository.findByLessonIdAndUserId(lessonId, callerUserId)
                    .orElseGet(() -> {
                        LessonProgress fresh = new LessonProgress();
                        fresh.setLessonId(lessonId);
                        fresh.setUserId(callerUserId);
                        return fresh;
                    });
            if (progress.getStatus() != LessonProgressStatus.COMPLETED) {
                progress.setStatus(LessonProgressStatus.COMPLETED);
                progress.setCompletedAt(now);
            }
            progressRepository.save(progress);
        }

        log.info("[lessons/quiz] user {} scored {}/{} on lesson {} ({})",
                callerUserId, score, total, lessonId, passed ? "PASS" : "fail");
        return new LessonQuizResultResponse(score, total, passed, PASS_MARK_PERCENT, now);
    }

    private LessonQuizQuestionResponse toResponse(LessonQuizQuestion q) {
        return new LessonQuizQuestionResponse(q.getId(), q.getQuestionText(), fromJson(q.getOptions()), q.getSortOrder());
    }

    private String toJson(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize quiz options", e);
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, String.class);
            return objectMapper.readValue(json, listType);
        } catch (Exception e) {
            log.warn("[lessons/quiz] unparseable options JSON on a question, treating as empty");
            return List.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
