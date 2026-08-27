package com.moriah.skillhub.attendance;

import com.moriah.skillhub.attendance.dto.AttendanceResponse;
import com.moriah.skillhub.attendance.dto.CheckinRequest;
import com.moriah.skillhub.attendance.dto.OverrideAttendanceRequest;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** build-plan.md feature 13. {@code checkin} is student self-service; {@code attendance}
 * (override) and the batch roster read are {@code TRAINER_PM}/{@code ADMIN} only, ownership
 * enforced in the service layer. {@code /me} is {@code STUDENT}-only — a PM/Admin has no {@code
 * batch_students} row of their own to read. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/api/v1/standups/{id}/checkin")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Self check in to a standup")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkin(
            @PathVariable Long id, @Valid @RequestBody CheckinRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(attendanceService.checkin(callerUserId, id, request)));
    }

    @PostMapping("/api/v1/standups/{id}/attendance")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "PM override of a student's attendance status")
    public ResponseEntity<ApiResponse<AttendanceResponse>> override(
            @PathVariable Long id, @Valid @RequestBody OverrideAttendanceRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(attendanceService.override(callerUserId, id, request)));
    }

    @GetMapping("/api/v1/attendance/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My own attendance history")
    public ResponseEntity<ApiResponse<PageResponse<AttendanceResponse>>> me(
            @CurrentUser Long callerUserId, @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(attendanceService.me(callerUserId, pageable)));
    }

    @GetMapping("/api/v1/attendance/batch/{batchId}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Attendance roster for a batch")
    public ResponseEntity<ApiResponse<PageResponse<AttendanceResponse>>> batchRoster(
            @PathVariable Long batchId, @CurrentUser Long callerUserId, @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(attendanceService.batchRoster(callerUserId, batchId, pageable)));
    }
}
