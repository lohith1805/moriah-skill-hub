package com.moriah.skillhub.client.entity;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Named {@code ClientProject}, not {@code Project} — {@code
 * com.moriah.skillhub.project.entity.Project} is feature 15's completely unrelated
 * content-catalog entity; architecture.md's own {@code client_projects} table name exists for
 * exactly this collision reason. {@code targetBatch} is a real, nullable {@code @ManyToOne
 * Batch} — the established shared-kernel exception ({@code Sprint.batch}, {@code
 * Certificate.batch}), nullable because build-plan.md feature 21 is explicit that a submitted
 * project may have no batch allocated yet ("If target_batch_id is null ... return an
 * empty/zeroed progress shape, not an error" — see {@code ClientProjectService#progress}).
 */
@Entity
@Table(name = "client_projects")
@Getter
@Setter
@NoArgsConstructor
public class ClientProject extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "scope_description", nullable = false, columnDefinition = "TEXT")
    private String scopeDescription;

    @Column(name = "budget_range", length = 50)
    private String budgetRange;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_batch_id")
    private Batch targetBatch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientProjectStatus status = ClientProjectStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
