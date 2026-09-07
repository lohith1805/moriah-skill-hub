package com.moriah.skillhub.assessment.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Unique {@code (attempt_id, question_id)} — one answer per question per attempt. {@code
 * givenAnswer} stores pre-serialized JSON: a JSON array of selected option indices for {@code
 * MCQ}/{@code MULTI_SELECT}, or a JSON string wrapping free-text for {@code CODE} — see {@code
 * GradingService} for the read/write shape. {@code isCorrect}/{@code marksAwarded} stay {@code
 * null} for an ungraded {@code CODE} answer.
 */
@Entity
@Table(name = "quiz_answers", uniqueConstraints =
    @UniqueConstraint(columnNames = {"attempt_id", "question_id"}))
@Getter
@Setter
@NoArgsConstructor
public class QuizAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id")
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private QuizQuestion question;

    @Column(name = "given_answer", columnDefinition = "JSON")
    private String givenAnswer;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "marks_awarded", precision = 5, scale = 2)
    private BigDecimal marksAwarded;
}
