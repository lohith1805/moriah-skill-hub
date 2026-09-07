package com.moriah.skillhub.assessment.repository;

import com.moriah.skillhub.assessment.entity.QuizAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

    @EntityGraph(attributePaths = "question")
    List<QuizAnswer> findByAttemptId(Long attemptId);
}
