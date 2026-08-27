package com.moriah.skillhub.pip.repository;

import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipRuleRepository extends JpaRepository<PipRule, Long> {

    Optional<PipRule> findByRuleCode(PipRuleCode ruleCode);

    /** {@code PipEvaluationService}'s whole rule set in one flat query, not one lookup per
     * evaluator (AGENTS.md). */
    List<PipRule> findByActiveTrue();
}
