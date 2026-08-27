package com.moriah.skillhub.project.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code createdBy} is a real {@code @ManyToOne User} — the established shared-kernel exception
 * (code-standards.md's own canonical {@code SprintService}/{@code Sprint} example: only {@code
 * User}/{@code Batch} get real cross-package associations, everything else stays a bare {@code
 * Long}). {@code techStack} is a pre-serialized JSON {@code String} column, parsed/serialized by
 * {@code ProjectService} via an injected {@code ObjectMapper} — same convention {@code
 * UserProfile}/{@code CodeReview}/{@code QuizQuestion} already established for a JSON column.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "tech_stack", columnDefinition = "JSON")
    private String techStack;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProjectDifficulty difficulty;

    @Column(length = 100)
    private String domain;

    @Column(name = "starter_repo_url", length = 500)
    private String starterRepoUrl;

    @Column(length = 10)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
