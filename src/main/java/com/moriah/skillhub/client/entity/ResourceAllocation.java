package com.moriah.skillhub.client.entity;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Maps a student/staff {@code user} and a {@code batch} onto a {@code clientProject} — both
 * real {@code @ManyToOne} associations, the established shared-kernel exception. No update/list/
 * delete endpoint exists in build-plan.md's feature 21 endpoint list — {@code POST
 * /ba/allocations} is create-only, matching {@code EmployeeService}'s own "no update/list
 * endpoint" precedent. */
@Entity
@Table(name = "resource_allocations")
@Getter
@Setter
@NoArgsConstructor
public class ResourceAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_project_id")
    private ClientProject clientProject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "role_in_project", nullable = false, length = 100)
    private String roleInProject;

    @Column(name = "allocated_days", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer allocatedDays;

    @Column(name = "story_points_estimate", columnDefinition = "SMALLINT UNSIGNED")
    private Integer storyPointsEstimate;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;
}
