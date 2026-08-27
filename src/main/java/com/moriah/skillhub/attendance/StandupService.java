package com.moriah.skillhub.attendance;

import com.moriah.skillhub.attendance.dto.CreateStandupRequest;
import com.moriah.skillhub.attendance.dto.StandupResponse;
import com.moriah.skillhub.attendance.dto.UpdateStandupRequest;
import com.moriah.skillhub.attendance.entity.Standup;
import com.moriah.skillhub.attendance.entity.StandupStatus;
import com.moriah.skillhub.attendance.repository.StandupRepository;
import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * PM-driven standup scheduling and cancellation. {@code AttendanceFinalisationJob} is the only
 * writer of {@code status = CONDUCTED} — no method here ever sets it (`/architect feature 13`
 * decision, see {@code Standup}'s Javadoc). {@code BatchRepository} injected directly ({@code
 * Batch} is the established shared-kernel type, same pattern {@code SprintService} uses);
 * {@code BatchService} separately for the ownership check.
 */
@Service
@RequiredArgsConstructor
public class StandupService {

    private final StandupRepository standupRepository;
    private final BatchRepository batchRepository;
    private final BatchService batchService;

    @Value("${moriah.jobs.zone}")
    private String jobsZone;

    @Transactional
    public StandupResponse create(Long callerUserId, CreateStandupRequest request) {
        Batch batch = requireBatch(request.batchId());
        batchService.requireOwnerOrAdmin(callerUserId, batch);

        Standup standup = new Standup();
        standup.setBatch(batch);
        standup.setSprintId(request.sprintId());
        standup.setScheduledAt(request.scheduledAt());
        standup.setLateCutoffMinutes(request.lateCutoffMinutes() != null
                ? request.lateCutoffMinutes() : Constants.ATTENDANCE_DEFAULT_LATE_CUTOFF_MINUTES);
        standup.setNotes(request.notes());
        standup.setStatus(StandupStatus.SCHEDULED);
        standupRepository.save(standup);

        return toResponse(standup);
    }

    /** {@code date}, when supplied, is interpreted as a calendar day in {@code moriah.jobs.zone}
     * (Asia/Kolkata) — {@code scheduled_at} is a precise instant, not a date column, so the
     * repository query takes an instant range, not a raw {@code DATE()} comparison. */
    @Transactional(readOnly = true)
    public PageResponse<StandupResponse> list(Long batchId, LocalDate date, Pageable pageable) {
        Instant startAt = null;
        Instant endAt = null;
        if (date != null) {
            ZoneId zone = ZoneId.of(jobsZone);
            startAt = date.atStartOfDay(zone).toInstant();
            endAt = date.plusDays(1).atStartOfDay(zone).toInstant();
        }
        return PageResponse.from(standupRepository.search(batchId, startAt, endAt, pageable).map(this::toResponse));
    }

    /** The only legal transition this endpoint ever performs is {@code SCHEDULED -> CANCELLED}
     * (`/architect feature 13` decision) — a same-status request is a no-op that still lets a PM
     * edit {@code notes}/{@code lateCutoffMinutes} (mirrors {@code SprintService.transitionStatus}'s
     * reasoning for {@link SprintService#update}). Once a standup has moved past {@code
     * SCHEDULED} — {@code CONDUCTED} (job-only) or already {@code CANCELLED} — nothing about it
     * is editable here; there's no legitimate reason to un-cancel or edit a standup that already
     * happened. */
    @Transactional
    public StandupResponse update(Long callerUserId, Long standupId, UpdateStandupRequest request) {
        Standup standup = requireStandup(standupId);
        batchService.requireOwnerOrAdmin(callerUserId, standup.getBatch());

        if (standup.getStatus() != StandupStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.STANDUP_INVALID_TRANSITION,
                    "Cannot edit a standup that is not SCHEDULED (current status: %s).".formatted(standup.getStatus()));
        }
        if (request.status() != StandupStatus.SCHEDULED && request.status() != StandupStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.STANDUP_INVALID_TRANSITION,
                    "Cannot move a standup from SCHEDULED to %s directly.".formatted(request.status()));
        }

        // `/review` fix: request.notes() is optional (only @Size, no @NotBlank) — the natural
        // minimal PUT body to just cancel a standup ({"status":"CANCELLED"}) has no notes field
        // at all. Falling back to the existing value, like lateCutoffMinutes already does, is
        // what stops that request from silently wiping out an existing note.
        if (request.notes() != null) {
            standup.setNotes(request.notes());
        }
        standup.setLateCutoffMinutes(request.lateCutoffMinutes() != null
                ? request.lateCutoffMinutes() : standup.getLateCutoffMinutes());
        standup.setStatus(request.status());

        return toResponse(standup);
    }

    Standup requireStandup(Long standupId) {
        return standupRepository.findById(standupId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STANDUP_NOT_FOUND, standupId));
    }

    private Batch requireBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, batchId));
    }

    StandupResponse toResponse(Standup standup) {
        return new StandupResponse(
                standup.getId(),
                standup.getBatch().getId(),
                standup.getSprintId(),
                standup.getScheduledAt(),
                standup.getLateCutoffMinutes(),
                standup.getNotes(),
                standup.getStatus(),
                standup.getFinalisedAt());
    }
}
