package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.hr.dto.MarkStaffAttendanceRequest;
import com.moriah.skillhub.hr.dto.StaffAttendanceResponse;
import com.moriah.skillhub.hr.dto.StaffAttendanceSummaryRow;
import com.moriah.skillhub.hr.dto.StaffCheckinRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * HR staff attendance (frontend gap — {@code hr/AttendanceLeave.jsx}). Mirrors {@code
 * LeaveController}: {@code @PreAuthorize("isAuthenticated()")} on the self-service routes with
 * server-side "own data unless you're HR" scoping in {@link StaffAttendanceService}; the status
 * override and the monthly summary are HR_MANAGER/ADMIN only.
 */
@RestController
@RequestMapping("/api/v1/hr/attendance")
@RequiredArgsConstructor
@Tag(name = "HR")
public class StaffAttendanceController {

    private final StaffAttendanceService staffAttendanceService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List staff attendance — a non-HR caller sees only their own; HR_MANAGER / "
            + "ADMIN see all, optionally filtered by userUuid and a from/to date range")
    public ResponseEntity<ApiResponse<PageResponse<StaffAttendanceResponse>>> list(
            @RequestParam(required = false) String userUuid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @CurrentUser Long callerUserId,
            @PageableDefault(size = 50) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                staffAttendanceService.list(userUuid, from, to, callerUserId, pageable)));
    }

    @PostMapping("/checkin")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Record a check-in for today — your own, or (HR_MANAGER/ADMIN) another "
            + "staff member's biometric-terminal check-in. Idempotent for the day.")
    public ResponseEntity<ApiResponse<StaffAttendanceResponse>> checkin(
            @Valid @RequestBody StaffCheckinRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(staffAttendanceService.checkin(callerUserId, request)));
    }

    @PostMapping("/checkout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Record today's check-out — your own, or (HR_MANAGER/ADMIN) another staff member's")
    public ResponseEntity<ApiResponse<StaffAttendanceResponse>> checkout(
            @RequestParam(required = false) String userUuid, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(staffAttendanceService.checkout(callerUserId, userUuid)));
    }

    @PutMapping("/mark")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "Override a staff member's attendance status for a date (upsert, audited)")
    public ResponseEntity<ApiResponse<StaffAttendanceResponse>> mark(
            @Valid @RequestBody MarkStaffAttendanceRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(staffAttendanceService.mark(callerUserId, request)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "Per-employee attendance roll-up for a month (default: current month)")
    public ResponseEntity<ApiResponse<List<StaffAttendanceSummaryRow>>> summary(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {

        return ResponseEntity.ok(ApiResponse.success(
                staffAttendanceService.summary(month != null ? month : YearMonth.now())));
    }
}
