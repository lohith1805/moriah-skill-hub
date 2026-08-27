package com.moriah.skillhub.admin.dto;

import com.moriah.skillhub.user.entity.RoleCode;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateUserRolesRequest(@NotEmpty Set<RoleCode> roles) {
}
