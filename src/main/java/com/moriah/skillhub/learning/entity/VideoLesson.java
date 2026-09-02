package com.moriah.skillhub.learning.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single self-paced video lesson (gap B1.4). {@code createdBy} is a bare user id — a content
 * row only needs it for an ownership check ({@code LearningResource}/{@code Notification} do the
 * same). {@code moduleName} is a free-text grouping the FE renders as a section header;
 * {@code sortOrder} orders lessons within a module.
 */
@Entity
@Table(name = "video_lessons")
@Getter
@Setter
@NoArgsConstructor
public class VideoLesson extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "module_name", nullable = false, length = 120)
    private String moduleName;

    @Column(name = "video_url", nullable = false, length = 1000)
    private String videoUrl;

    // columnDefinition matches V22's INT UNSIGNED exactly — same reasoning as SubscriptionPlan's
    // UNSIGNED columns (Hibernate's default INTEGER mapping fails ddl-auto: validate otherwise).
    @Column(name = "duration_seconds", columnDefinition = "INT UNSIGNED")
    private Integer durationSeconds;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "is_published", nullable = false)
    private boolean published;
}
