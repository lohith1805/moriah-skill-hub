package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.hr.dto.MarkStaffAttendanceRequest;
import com.moriah.skillhub.hr.dto.StaffAttendanceResponse;
import com.moriah.skillhub.hr.dto.StaffAttendanceSummaryProjection;
import com.moriah.skillhub.hr.dto.StaffAttendanceSummaryRow;
import com.moriah.skillhub.hr.dto.StaffCheckinRequest;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.EmployeeStatus;
import com.moriah.skillhub.hr.entity.StaffAttendance;
import com.moriah.skillhub.hr.entity.StaffAttendanceStatus;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.hr.repository.StaffAttendanceRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HR staff attendance ledger (frontend gap). Mirrors {@code LeaveService}'s "own data unless
 * you're HR" scoping: {@code GET} auto-scopes a non-HR caller to their own rows; a self check-in
 * needs no role, logging on someone else's behalf or overriding a status is HR_MANAGER/ADMIN
 * only. Students are not tracked here — their standup attendance is {@code AttendanceService}.
 */
@Service
@RequiredArgsConstructor
public class StaffAttendanceService {

    /** After this local time, a check-in is {@code LATE} rather than {@code PRESENT}. Not spelled
     * out anywhere — a reasonable office start-of-day grace, matching the spirit of {@code
     * standups.late_cutoff_minutes}. */
    private static final LocalTime LATE_AFTER = LocalTime.of(10, 0);

    private final StaffAttendanceRepository staffAttendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Value("${moriah.jobs.zone}")
    private String zoneId;

    private ZoneId zone() {
        return ZoneId.of(zoneId);
    }

    // ---- check in / out --------------------------------------------------

    @Transactional
    public StaffAttendanceResponse checkin(Long callerUserId, StaffCheckinRequest request) {
        User target = resolveTarget(callerUserId, request.userUuid());
        requireEmployee(target.getId());

        LocalDate today = LocalDate.now(zone());
        Instant now = Instant.now();
        boolean onBehalf = !target.getId().equals(callerUserId);

        StaffAttendance row = staffAttendanceRepository.findByUserIdAndWorkDate(target.getId(), today)
                .orElseGet(() -> {
                    StaffAttendance fresh = new StaffAttendance();
                    fresh.setUser(target);
                    fresh.setWorkDate(today);
                    return fresh;
                });

        // Idempotent: a second check-in the same day doesn't move the time or downgrade a status
        // an HR user may have already corrected.
        if (row.getCheckedInAt() == null) {
            row.setCheckedInAt(now);
            row.setStatus(isLate(now) ? StaffAttendanceStatus.LATE : StaffAttendanceStatus.PRESENT);
            if (request.device() != null && !request.device().isBlank()) {
                row.setDevice(request.device());
            }
            if (onBehalf) {
                row.setMarkedBy(userRepository.getReferenceById(callerUserId));
            }
            staffAttendanceRepository.save(row);
        }
        return toResponse(row);
    }

    @Transactional
    public StaffAttendanceResponse checkout(Long callerUserId, String userUuid) {
        User target = resolveTarget(callerUserId, userUuid);
        LocalDate today = LocalDate.now(zone());

        StaffAttendance row = staffAttendanceRepository.findByUserIdAndWorkDate(target.getId(), today)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STAFF_ATTENDANCE_NOT_FOUND, target.getId()));

        row.setCheckedOutAt(Instant.now());
        staffAttendanceRepository.save(row);
        return toResponse(row);
    }

    // ---- list ----------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<StaffAttendanceResponse> list(String userUuid, LocalDate from, LocalDate to,
                                                      Long callerUserId, Pageable pageable) {
        Long scopeUserId;
        if (isHr()) {
            scopeUserId = (userUuid == null || userUuid.isBlank()) ? null
                    : userRepository.findByUuid(userUuid).map(User::getId)
                            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userUuid));
        } else {
            scopeUserId = callerUserId;
        }

        Page<StaffAttendance> page = staffAttendanceRepository.search(scopeUserId, from, to, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    // ---- HR status override ------------------------------------------

    @Transactional
    public StaffAttendanceResponse mark(Long callerUserId, MarkStaffAttendanceRequest request) {
        User target = userRepository.findByUuid(request.userUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, request.userUuid()));
        requireEmployee(target.getId());

        StaffAttendance row = staffAttendanceRepository.findByUserIdAndWorkDate(target.getId(), request.workDate())
                .orElseGet(() -> {
                    StaffAttendance fresh = new StaffAttendance();
                    fresh.setUser(target);
                    fresh.setWorkDate(request.workDate());
                    return fresh;
                });

        StaffAttendanceStatus oldStatus = row.getId() != null ? row.getStatus() : null;
        row.setStatus(request.status());
        if (request.notes() != null) {
            row.setNotes(request.notes());
        }
        row.setMarkedBy(userRepository.getReferenceById(callerUserId));
        staffAttendanceRepository.save(row);

        auditLogService.record(callerUserId, "STAFF_ATTENDANCE_OVERRIDE", "StaffAttendance", row.getId(),
                oldStatus, request.status());

        return toResponse(row);
    }

    // ---- monthly ledger roll-up ------------------------------------

    @Transactional(readOnly = true)
    public List<StaffAttendanceSummaryRow> summary(YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        Map<Long, StaffAttendanceSummaryProjection> byUser = new HashMap<>();
        for (StaffAttendanceSummaryProjection p : staffAttendanceRepository.summarise(from, to)) {
            byUser.put(p.getUserId(), p);
        }

        List<Employee> employees = employeeRepository
                .search(EmployeeStatus.ACTIVE, null, null, Pageable.unpaged())
                .getContent();

        return employees.stream().map(e -> {
            StaffAttendanceSummaryProjection p = byUser.get(e.getUser().getId());
            long present = p == null ? 0 : p.getPresentDays();
            long late = p == null ? 0 : p.getLateDays();
            long absent = p == null ? 0 : p.getAbsentDays();
            long half = p == null ? 0 : p.getHalfDays();
            long onLeave = p == null ? 0 : p.getOnLeaveDays();

            long counted = present + late + absent + half;
            Integer pct = counted == 0 ? null
                    : (int) Math.round((present + late) * 100.0 / counted);

            return new StaffAttendanceSummaryRow(
                    e.getUser().getUuid(), e.getUser().getFullName(), e.getDepartment(),
                    present, late, absent, half, onLeave, pct);
        }).toList();
    }

    // ---- helpers -----------------------------------------------------

    /** Self by default; an HR_MANAGER/ADMIN may target another staff member by uuid. */
    private User resolveTarget(Long callerUserId, String userUuid) {
        if (userUuid == null || userUuid.isBlank()) {
            return userRepository.findById(callerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, callerUserId));
        }
        User target = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userUuid));
        if (!target.getId().equals(callerUserId) && !isHr()) {
            throw new ForbiddenOperationException(ErrorCode.INSUFFICIENT_ROLE);
        }
        return target;
    }

    private void requireEmployee(Long userId) {
        if (!employeeRepository.existsByUserId(userId)) {
            throw new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, userId);
        }
    }

    private boolean isHr() {
        List<String> roles = SecurityUtils.currentUserRoles();
        return roles.contains(RoleCode.HR_MANAGER.name()) || roles.contains(RoleCode.ADMIN.name());
    }

    private boolean isLate(Instant checkinAt) {
        return checkinAt.atZone(zone()).toLocalTime().isAfter(LATE_AFTER);
    }

    private StaffAttendanceResponse toResponse(StaffAttendance a) {
        User user = a.getUser();
        User markedBy = a.getMarkedBy();
        return new StaffAttendanceResponse(
                a.getId(),
                user.getUuid(),
                user.getFullName(),
                a.getWorkDate(),
                a.getCheckedInAt(),
                a.getCheckedOutAt(),
                a.getStatus(),
                a.getDevice(),
                markedBy != null ? markedBy.getUuid() : null,
                a.getNotes(),
                a.getCreatedAt());
    }
}
