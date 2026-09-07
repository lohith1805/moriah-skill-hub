package com.moriah.skillhub.attendance;

import com.moriah.skillhub.attendance.dto.CreateStandupRequest;
import com.moriah.skillhub.attendance.dto.StandupResponse;
import com.moriah.skillhub.attendance.dto.UpdateStandupRequest;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** build-plan.md feature 13. Mutation endpoints are {@code TRAINER_PM}/{@code ADMIN} only,
 * ownership enforced in the service layer via {@code BatchService.requireOwnerOrAdmin} (same
 * reasoning {@code SprintController}'s own Javadoc gives). {@code GET} is also open to {@code
 * STUDENT} — they need to see their own batch's schedule to know when to check in. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Standups")
public class StandupController {

    private final StandupService standupService;

    @PostMapping("/api/v1/standups")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Schedule a standup")
    public ResponseEntity<ApiResponse<StandupResponse>> create(
            @Valid @RequestBody CreateStandupRequest request, @CurrentUser Long callerUserId) {

        StandupResponse response = standupService.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/standups")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN','STUDENT')")
    @Operation(summary = "List standups for a batch, optionally scoped to one day")
    public ResponseEntity<ApiResponse<PageResponse<StandupResponse>>> list(
            @RequestParam Long batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(standupService.list(batchId, date, pageable)));
    }

    @PutMapping("/api/v1/standups/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Edit or cancel a SCHEDULED standup")
    public ResponseEntity<ApiResponse<StandupResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateStandupRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(standupService.update(callerUserId, id, request)));
    }
}
