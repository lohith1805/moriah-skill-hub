package com.moriah.skillhub.pip.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** {@code POST /api/v1/pip/{id}/milestones} — a PM adds a concrete recovery task to an open PIP
 * record's checklist. The nightly job seeds one generic milestone per trigger; the PM fleshes
 * out the rest here. */
public record CreatePipMilestoneRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull @FutureOrPresent LocalDate dueDate
) {
}
