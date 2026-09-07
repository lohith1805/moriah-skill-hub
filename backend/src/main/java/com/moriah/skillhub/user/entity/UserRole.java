package com.moriah.skillhub.user.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A pure join row — no {@code @ManyToOne} associations to {@link User}/{@link Role}, and no
 * {@code id}/{@code created_at}/{@code updated_at} (composite PK, matching {@code user_roles} in
 * {@code V1__core_users_roles.sql}). Role lookups join against {@link Role} directly in a
 * repository query rather than navigating an entity graph.
 */
@Entity
@Table(name = "user_roles")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    public UserRole(Long userId, Long roleId) {
        this.id = new UserRoleId(userId, roleId);
    }
}
