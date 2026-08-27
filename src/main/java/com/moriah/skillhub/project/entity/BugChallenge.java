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

/** {@code project} is a same-package {@code @ManyToOne}; {@code createdBy} is the same
 * shared-kernel {@code User} exception {@link Project#getCreatedBy()} uses. */
@Entity
@Table(name = "bug_challenges")
@Getter
@Setter
@NoArgsConstructor
public class BugChallenge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "broken_code_key", nullable = false, length = 255)
    private String brokenCodeKey;

    @Column(name = "expected_behaviour", nullable = false, columnDefinition = "TEXT")
    private String expectedBehaviour;

    @Column(name = "test_script_key", length = 255)
    private String testScriptKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProjectDifficulty difficulty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
