package com.moriah.skillhub.assessment.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One question in a {@link QuestionBank} (gap B1.15). {@code options}/{@code correctAnswer}
 * follow {@link QuizQuestion}'s conventions exactly — JSON array of option strings / JSON array
 * of 0-based indices, both {@code null} for {@code CODE}. {@code correctAnswer} is never
 * serialized into an API response.
 */
@Entity
@Table(name = "question_bank_items")
@Getter
@Setter
@NoArgsConstructor
public class QuestionBankItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id")
    private QuestionBank bank;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType questionType;

    @Column(columnDefinition = "JSON")
    private String options;

    @Column(name = "correct_answer", columnDefinition = "JSON")
    private String correctAnswer;

    // TINYINT UNSIGNED in V31 — same reasoning as QuizQuestion.marks.
    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer marks;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private QuestionDifficulty difficulty;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
