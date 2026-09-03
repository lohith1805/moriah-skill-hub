package com.moriah.skillhub.learning.repository;

import com.moriah.skillhub.learning.entity.LessonQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonQuizQuestionRepository extends JpaRepository<LessonQuizQuestion, Long> {

    List<LessonQuizQuestion> findByLessonIdOrderBySortOrderAscIdAsc(Long lessonId);

    long countByLessonId(Long lessonId);

    Optional<LessonQuizQuestion> findByIdAndLessonId(Long id, Long lessonId);
}
