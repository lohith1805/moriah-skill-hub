package com.moriah.skillhub.assessment.repository;

import com.moriah.skillhub.assessment.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    /** A quiz's full question set, one flat query — used both to build the attempt's question
     * view and, at submit/expiry time, to grade every question (code-standards.md "N+1
     * Prevention": never a repository call per question in a loop). */
    List<QuizQuestion> findByQuizIdOrderByIdAsc(Long quizId);

    /** {@code QuizAttemptExpiryJob}'s bulk read — every question across every quiz with an
     * expiring attempt this run, in one query, grouped by {@code quiz.id} in memory by the
     * caller. Not one {@link #findByQuizIdOrderByIdAsc} call per expired attempt. */
    List<QuizQuestion> findByQuizIdIn(List<Long> quizIds);
}
