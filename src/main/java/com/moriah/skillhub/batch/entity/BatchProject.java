package com.moriah.skillhub.batch.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A pure join row — one PUBLISHED, track-matching project a Trainer/PM has curated onto their
 * own batch's "Assign Projects" screen ({@code BatchService#assignProjects}). Not a
 * visibility/access-control table — {@code ProjectService#list} still shows every PUBLISHED
 * project to every student regardless of this table's contents; same "a join row is a fact, not a
 * navigable entity graph" reasoning {@code UserRole}/{@code LeadCampaignRecipient} already
 * establish. */
@Entity
@Table(name = "batch_projects")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BatchProject {

    @EmbeddedId
    private BatchProjectId id;

    public BatchProject(Long batchId, Long projectId) {
        this.id = new BatchProjectId(batchId, projectId);
    }
}
