package com.moriah.skillhub.pip.dto;

import com.moriah.skillhub.pip.entity.PipRuleCode;
import com.moriah.skillhub.pip.entity.PipSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/pip} — a PM raises a PIP by hand for a qualitative concern the six
 * nightly rules don't catch. {@code ruleCode} tags which recovery theme it belongs to (and
 * seeds the auto milestone's title); {@code reason} is the PM's own explanation. */
public record CreatePipRecordRequest(
        @NotBlank String studentUuid,
        @NotNull Long batchId,
        @NotNull PipRuleCode ruleCode,
        @NotBlank @Size(max = 2000) String reason,
        @NotNull PipSeverity severity
) {
}
