package com.moriah.skillhub.dev;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.pip.entity.PipRecord;
import com.moriah.skillhub.pip.repository.PipRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Dev-only PIP fixtures, so the feature-17 nightly behaviours can be demoed without waiting out a
 * real 15-day window.
 *
 * <p><b>Dev profile only</b> — {@code @Profile("dev")}, ADMIN-gated. See {@code
 * docs/testing-scheduled-jobs.md}.
 */
@RestController
@RequestMapping("/api/v1/dev/pip")
@RequiredArgsConstructor
@Profile("dev")
@Slf4j
@Tag(name = "Dev")
public class DevPipController {

    private final PipRecordRepository pipRecordRepository;

    /** Backdate a PIP's window so {@code end_date} is yesterday (window length preserved), and
     * clear {@code early_clear_nudged_at}. The next {@code POST /dev/jobs/pip-evaluation/run} then
     * treats the record as elapsed — exercising {@code autoResolveElapsed}'s auto-clear /
     * escalation path with no 15-day wait. */
    @PostMapping("/{id}/elapse-window")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "DEV ONLY — backdate a PIP record's window so its 15-day period has already "
            + "elapsed (end_date = yesterday), then run POST /dev/jobs/pip-evaluation/run to fire the "
            + "auto-clear / escalation pass without waiting.")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> elapseWindow(@PathVariable Long id) {
        PipRecord record = pipRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PIP_RECORD_NOT_FOUND));

        long windowDays = ChronoUnit.DAYS.between(record.getStartDate(), record.getEndDate());
        LocalDate newEnd = LocalDate.now().minusDays(1);
        record.setEndDate(newEnd);
        record.setStartDate(newEnd.minusDays(windowDays));
        record.setEarlyClearNudgedAt(null);

        log.warn("[dev/pip] PIP {} window backdated -> {} .. {}", id, record.getStartDate(), record.getEndDate());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "id", id,
                "startDate", record.getStartDate().toString(),
                "endDate", record.getEndDate().toString())));
    }
}
