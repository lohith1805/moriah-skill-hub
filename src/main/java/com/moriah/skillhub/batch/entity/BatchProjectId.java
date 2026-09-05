package com.moriah.skillhub.batch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Composite key for {@link BatchProject} — same shape as {@code UserRoleId}/{@code
 * LeadCampaignRecipientId}. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class BatchProjectId implements Serializable {

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "project_id")
    private Long projectId;
}
