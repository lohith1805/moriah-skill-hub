package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.LeaveStatus;
import jakarta.validation.constraints.NotNull;

public record LeaveDecisionRequest(@NotNull LeaveStatus decision) {
    public LeaveDecisionRequest {
        if (decision == LeaveStatus.PENDING) {
            throw new IllegalArgumentException("decision must be APPROVED or REJECTED");
        }
    }
}
