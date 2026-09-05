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

    /** Same free-string convention as {@code Batch.trackCode} (no shared enum — both are plain
     * columns, matched by equality). Lets a Trainer/PM curate which published projects show up on
     * their own batch's "Assign Projects" screen ({@code BatchService#assignProjects}) without
     * this being a project-visibility restriction — {@code ProjectService#list} still shows every
     * PUBLISHED project to every student regardless of track. */
    @Column(length = 50)
    private String track;

    @Column(name = "starter_repo_url", length = 500)
    private String starterRepoUrl;

    /** FRS MSH-FR-DEV-03 ("Upload Architecture diagrams, Swagger/OpenAPI specs, ... ER
     * diagrams... "): three optional reference links, same plain-URL convention as {@code
     * starterRepoUrl} rather than a new upload pipeline — a README is already covered by whatever
     * lives in the starter repo itself, so there's no separate readmeUrl. All three are nullable
     * and independently settable on an existing project, so a developer can add them any time
     * after creation, not only at authoring time. */
    @Column(name = "architecture_diagram_url", length = 500)
    private String architectureDiagramUrl;

    @Column(name = "api_spec_url", length = 500)
    private String apiSpecUrl;

    @Column(name = "er_diagram_url", length = 500)
    private String erDiagramUrl;

    @Column(name = "readme_content", columnDefinition = "LONGTEXT")
    private String readmeContent;

    /** FRS MSH-FR-DEV-03: a reference-solution branch link and a Loom/YouTube/Vimeo tutorial
     * embed link, same plain-URL convention as {@code architectureDiagramUrl} et al. — no upload
     * pipeline, independently settable on an existing project at any time. */
    @Column(name = "reference_solution_url", length = 500)
    private String referenceSolutionUrl;

    @Column(name = "video_tutorial_url", length = 500)
    private String videoTutorialUrl;

    @Column(length = 10)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
