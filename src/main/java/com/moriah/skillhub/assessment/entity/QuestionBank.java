package com.moriah.skillhub.assessment.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A reusable pool of assessment questions (gap B1.15). {@code createdBy} is a bare user id — a
 * catalogue row only needs it for an ownership check. Items live in {@link QuestionBankItem}.
 */
@Entity
@Table(name = "question_banks")
@Getter
@Setter
@NoArgsConstructor
public class QuestionBank extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
