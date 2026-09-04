package com.moriah.skillhub.resource.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One curated learning link in the Resource Library (gap B1.6). {@code createdBy} is a bare
 * {@code Long} user id, not a {@code @ManyToOne User} — a catalogue row only ever needs the id
 * for an ownership check, the same {@code Notification.userId} / {@code Payment.planId} reasoning
 * (architecture.md: {@code common/} never imports a feature package, and even feature packages
 * keep a plain id where no navigation is needed).
 * <p>
 * {@code tags} is pre-serialized JSON array text, parsed/serialized by the service with {@code
 * ObjectMapper} — the same treatment {@code Project.techStack} / {@code UserProfile.skills}
 * already use for a JSON column.
 */
@Entity
@Table(name = "learning_resources")
@Getter
@Setter
@NoArgsConstructor
public class LearningResource extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceCategory category;

    /** Cohort track this resource is for — a free string matching {@code batches.track_code}.
     * {@code null} = every track sees it. */
    @Column(length = 30)
    private String track;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(columnDefinition = "JSON")
    private String tags;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
