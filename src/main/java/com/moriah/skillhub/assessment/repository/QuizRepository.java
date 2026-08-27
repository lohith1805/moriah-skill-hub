package com.moriah.skillhub.assessment.repository;

import com.moriah.skillhub.assessment.entity.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /** Backs {@code GET /api/v1/assessments?batchId=} — {@code batchId} required, same "required
     * scope parameter" precedent as {@code SprintRepository}'s {@code GET /sprints?batchId=}. */
    Page<Quiz> findByBatchId(Long batchId, Pageable pageable);
}
