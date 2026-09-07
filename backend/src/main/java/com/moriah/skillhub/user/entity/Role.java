package com.moriah.skillhub.user.entity;

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
 * Reference data, seeded by {@code V4__seed_roles_permissions.sql}. {@code code} has no CHECK
 * constraint (unlike a transient status column) — the row set itself is the source of truth,
 * consistent with any other reference table.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private RoleCode code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;
}
