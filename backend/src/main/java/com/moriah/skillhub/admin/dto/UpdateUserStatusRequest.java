package com.moriah.skillhub.admin.dto;

import com.moriah.skillhub.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
