package com.moriah.skillhub.project.entity;

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

/** {@code project} is a same-package {@code @ManyToOne} — no shared-kernel exception needed,
 * unlike {@code Project.createdBy}. Exactly one of {@code fileKey}/{@code externalUrl} is
 * non-null — V8's own {@code chk_project_assets_source} CHECK enforces this at the DB level too,
 * but {@code ProjectService#addAsset}'s imperative check validates it first for a clean 400
 * instead of a raw SQL constraint violation (a multipart endpoint, not a JSON request body, so
 * there's no compact-constructor DTO to put this on). */
@Entity
@Table(name = "project_assets")
@Getter
@Setter
@NoArgsConstructor
public class ProjectAsset extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    private AssetType assetType;

    @Column(length = 200)
    private String title;

    @Column(name = "file_key", length = 255)
    private String fileKey;

    @Column(name = "external_url", length = 500)
    private String externalUrl;

    // SMALLINT UNSIGNED in V8 — see Standup.lateCutoffMinutes' Javadoc for why this needs an
    // explicit columnDefinition override.
    @Column(name = "sort_order", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer sortOrder = 0;
}
