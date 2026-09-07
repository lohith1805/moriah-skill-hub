package com.moriah.skillhub.attendance.dto;

import jakarta.validation.constraints.Size;

public record CheckinRequest(
        @Size(max = 2000) String blockerNotes
) {
}
