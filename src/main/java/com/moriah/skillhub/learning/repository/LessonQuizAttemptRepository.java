package com.moriah.skillhub.learning.repository;

import com.moriah.skillhub.learning.entity.LessonQuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LessonQuizAttemptRepository extends JpaRepository<LessonQuizAttempt, Long> {

    Optional<LessonQuizAttempt> findByLessonIdAndUserId(Long lessonId, Long userId);
}
