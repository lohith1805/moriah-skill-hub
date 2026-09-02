package com.moriah.skillhub.assessment.repository;

import com.moriah.skillhub.assessment.entity.QuestionBankItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankItemRepository extends JpaRepository<QuestionBankItem, Long> {

    Page<QuestionBankItem> findByBankId(Long bankId, Pageable pageable);

    long countByBankId(Long bankId);
}
