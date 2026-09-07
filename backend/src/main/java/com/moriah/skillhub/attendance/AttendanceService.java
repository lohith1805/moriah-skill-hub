package com.moriah.skillhub.attendance;

import com.moriah.skillhub.attendance.dto.AttendanceResponse;
import com.moriah.skillhub.attendance.dto.CheckinRequest;
import com.moriah.skillhub.attendance.dto.OverrideAttendanceRequest;
import com.moriah.skillhub.attendance.entity.Attendance;
import com.moriah.skillhub.attendance.entity.AttendanceStatus;
import com.moriah.skillhub.attendance.entity.Standup;
import com.moriah.skillhub.attendance.entity.StandupStatus;
import com.moriah.skillhub.attendance.repository.AttendanceRepository;
import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Self check-in and PM override. Unlike {@code SubmissionService.create} (feature 12), {@link
 * #checkin} makes no outbound HTTP call, so it carries a normal {@code @Transactional} — {@link
 * AttendanceWriter#tryInsert}/{@link AttendanceWriter#findExisting} still isolate the actual
 * insert into their own {@code REQUIRES_NEW} transaction (suspending this one), which is what
 * the idempotent-insert pattern actually needs; there's no AGENTS.md "no HTTP call inside a
 * transaction" reason to leave the outer method unwrapped here.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceWriter attendanceWriter;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final UserRepository userRepository;
    private final StandupService standupService;
    private final AuditLogService auditLogService;

    /** build-plan.md feature 13: "Self check-in: on time -> PRESENT, after cutoff -> LATE" and
     * "double check-in is idempotent, not an error." Ineligibility (wrong batch, cancelled or
     * already-finalised standup) is checked before the insert attempt — no reason to race the
     * unique constraint for a request that can never legally succeed. */
    @Transactional
    public AttendanceResponse checkin(Long callerUserId, Long standupId, CheckinRequest request) {
        Standup standup = standupService.requireStandup(standupId);
        requireCheckinable(standup);

        if (!batchService.isActiveMember(standup.getBatch().getId(), callerUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_BATCH_MEMBER);
        }

        User student = requireUser(callerUserId);
        Instant now = Instant.now();

        Attendance attendance = new Attendance();
        attendance.setStandup(standup);
        attendance.setUser(student);
        attendance.setStatus(isLate(standup, now) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT);
        attendance.setCheckedInAt(now);
        attendance.setBlockerNotes(request.blockerNotes());
        attendance.setAutoMarked(false);

        Attendance saved;
        try {
            // AttendanceWriter, not attendanceRepository directly — see its own Javadoc.
            saved = attendanceWriter.tryInsert(attendance);
        } catch (DataIntegrityViolationException | CannotAcquireLockException e) {
            // Two exception types, not one — MySQL/InnoDB can resolve two transactions
            // concurrently inserting the same unique key as a genuine deadlock rather than a
            // clean duplicate-key rejection (confirmed the hard way, feature 12). Either way the
            // winning transaction has already committed by the time we get here.
            saved = attendanceWriter.findExisting(standupId, callerUserId).orElseThrow(() -> e);
        }

        return toResponse(saved);
    }

    /** build-plan.md feature 13: "PM override on any status ... audited with old and new value."
     * Upserts (`/architect feature 13` decision) — creates a row for a student who never checked
     * in, updates one that already exists, either way audited. Not routed through {@code
     * AttendanceWriter}: a PM correcting one named student's row has no concurrent-duplicate race
     * to guard against the way an anonymous double-POST check-in does. */
    @Transactional
    public AttendanceResponse override(Long callerUserId, Long standupId, OverrideAttendanceRequest request) {
        Standup standup = standupService.requireStandup(standupId);
        batchService.requireOwnerOrAdmin(callerUserId, standup.getBatch());

        if (standup.getStatus() == StandupStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.STANDUP_CANCELLED);
        }

        User student = requireUserByUuid(request.userUuid());
        User pm = requireUser(callerUserId);

        Attendance attendance = attendanceRepository.findByStandupIdAndUserId(standup.getId(), student.getId())
                .orElseGet(() -> {
                    Attendance created = new Attendance();
                    created.setStandup(standup);
                    created.setUser(student);
                    return created;
                });

        AttendanceStatus oldStatus = attendance.getId() != null ? attendance.getStatus() : null;

        attendance.setStatus(request.status());
        if (request.blockerNotes() != null) {
            attendance.setBlockerNotes(request.blockerNotes());
        }
        attendance.setMarkedBy(pm);
        attendance.setAutoMarked(false);
        attendanceRepository.save(attendance);

        auditLogService.record(callerUserId, "ATTENDANCE_OVERRIDE", "Attendance", attendance.getId(),
                oldStatus, request.status());

        return toResponse(attendance);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> me(Long callerUserId, Pageable pageable) {
        return PageResponse.from(attendanceRepository.findByUserId(callerUserId, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> batchRoster(Long callerUserId, Long batchId, Pageable pageable) {
        Batch batch = requireBatch(batchId);
        batchService.requireOwnerOrAdmin(callerUserId, batch);
        return PageResponse.from(attendanceRepository.findByBatch(batchId, pageable).map(this::toResponse));
    }

    private void requireCheckinable(Standup standup) {
        if (standup.getStatus() == StandupStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.STANDUP_CANCELLED);
        }
        if (standup.getFinalisedAt() != null) {
            throw new BusinessException(ErrorCode.STANDUP_ALREADY_FINALISED);
        }
    }

    /** build-plan.md feature 13: "on time -> PRESENT, after cutoff -> LATE," read per-standup —
     * {@code late_cutoff_minutes} is a column on {@code Standup}, never a global constant
     * (V7 migration's own comment: "AttendanceFinalisationJob reads this per-standup"). */
    private boolean isLate(Standup standup, Instant checkinAt) {
        Instant cutoff = standup.getScheduledAt().plus(Duration.ofMinutes(standup.getLateCutoffMinutes()));
        return checkinAt.isAfter(cutoff);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
    }

    private User requireUserByUuid(String uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, uuid));
    }

    private Batch requireBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, batchId));
    }

    private AttendanceResponse toResponse(Attendance attendance) {
        Standup standup = attendance.getStandup();
        User user = attendance.getUser();
        User markedBy = attendance.getMarkedBy();
        return new AttendanceResponse(
                attendance.getId(),
                standup.getId(),
                standup.getBatch().getId(),
                standup.getScheduledAt(),
                user.getUuid(),
                user.getFullName(),
                attendance.getStatus(),
                attendance.getCheckedInAt(),
                attendance.getBlockerNotes(),
                markedBy != null ? markedBy.getUuid() : null,
                attendance.isAutoMarked());
    }
}
