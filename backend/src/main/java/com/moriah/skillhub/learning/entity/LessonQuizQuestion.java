package com.moriah.skillhub.learning.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One MCQ in a lesson's quiz (gap B1.4). {@code options} is pre-serialized JSON array text
 * handled only by the service; {@code correctIndex} is the 0-based winning option and is never
 * serialized into a student-facing response (same stance as {@code QuestionBankItem}).
 * {@code lessonId}/{@code createdBy} are bare ids.
 */
@Entity
@Table(name = "lesson_quiz_questions")
@Getter
@Setter
@NoArgsConstructor
public class LessonQuizQuestion extends BaseEntity {

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false, columnDefinition = "JSON")
    private String options;

    @Column(name = "correct_index", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer correctIndex;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
