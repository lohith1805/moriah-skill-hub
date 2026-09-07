package com.moriah.skillhub.assessment.entity;

/** Mirrors {@code chk_question_bank_items_difficulty} in {@code V31__question_banks.sql}.
 * Nullable on the entity — a bank item need not be graded for difficulty. */
public enum QuestionDifficulty {
    EASY,
    MEDIUM,
    HARD
}
