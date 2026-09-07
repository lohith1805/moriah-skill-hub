package com.moriah.skillhub.assessment.repository;

import com.moriah.skillhub.assessment.entity.QuestionBank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {

    /** {@code GET /api/v1/assessments/banks}. Both filters optional. */
    @Query("""
            SELECT b FROM QuestionBank b
             WHERE (:topic IS NULL OR LOWER(b.topic) LIKE LOWER(CONCAT('%', :topic, '%')))
               AND (:active IS NULL OR b.active = :active)
            """)
    Page<QuestionBank> search(@Param("topic") String topic,
                              @Param("active") Boolean active,
                              Pageable pageable);
}
