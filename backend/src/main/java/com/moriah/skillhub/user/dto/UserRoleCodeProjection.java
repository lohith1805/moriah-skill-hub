package com.moriah.skillhub.user.dto;

import com.moriah.skillhub.user.entity.RoleCode;

/** feature 22: {@code AdminUserService.list}'s batched role lookup for a whole page of users in
 * one query — the same "project into a record, never fetch full entities" rule code-standards.md's
 * repository example (`VelocityProjection`) already establishes, applied to a two-column pair
 * instead of a single value. */
public record UserRoleCodeProjection(Long userId, RoleCode roleCode) {
}
