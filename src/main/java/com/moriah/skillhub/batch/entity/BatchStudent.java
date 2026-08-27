package com.moriah.skillhub.batch.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "batch_students", uniqueConstraints =
    @UniqueConstraint(columnNames = {"batch_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class BatchStudent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStudentStatus status = BatchStudentStatus.ACTIVE;

    @Column(name = "graduated_at")
    private Instant graduatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graduated_by")
    private User graduatedBy;

    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;
}
