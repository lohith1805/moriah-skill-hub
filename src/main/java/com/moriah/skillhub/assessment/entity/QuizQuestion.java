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
 * {@code options}/{@code correctAnswer} store pre-serialized JSON text, same treatment as
 * {@code UserProfile.skills}/{@code CodeReview.inlineComments} — {@code QuizService}/{@code
 * GradingService} are the only things that parse or write them, nothing queries into them.
 * {@code options} is a JSON array of option strings (null for {@code CODE}); {@code
 * correctAnswer} is a JSON array of 0-based indices into {@code options} (null for {@code CODE}).
 * {@code correctAnswer} is never serialized into any API response — see {@code QuizService}'s
 * response-mapping methods, none of which read this field.
 */
@Entity
@Table(name = "quiz_questions")
@Getter
@Setter
@NoArgsConstructor
public class QuizQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType questionType;

    @Column(columnDefinition = "JSON")
    private String options;

    @Column(name = "correct_answer", columnDefinition = "JSON")
    private String correctAnswer;

    // TINYINT UNSIGNED in V8 — see Standup.lateCutoffMinutes' Javadoc.
    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer marks;

    @Column(columnDefinition = "TEXT")
    private String explanation;
}
