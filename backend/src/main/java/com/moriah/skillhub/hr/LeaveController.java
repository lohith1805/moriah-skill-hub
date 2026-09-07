package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.hr.dto.CreateLeaveRequest;
import com.moriah.skillhub.hr.dto.LeaveDecisionRequest;
import com.moriah.skillhub.hr.dto.LeaveRequestResponse;
import com.moriah.skillhub.hr.entity.LeaveStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

@RestController
@RequestMapping("/api/v1/hr/leaves")
@RequiredArgsConstructor
@Tag(name = "HR")
public class LeaveController {

    private final LeaveService leaveService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List leave requests — an employee sees only their own; HR_MANAGER / ADMIN "
            + "see all, optionally filtered by status and userUuid")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestResponse>>> list(
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) String userUuid,
            @CurrentUser Long callerUserId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                leaveService.list(userUuid, status, callerUserId, pageable)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> create(
            @Valid @RequestBody CreateLeaveRequest request,
            @CurrentUser Long callerUserId) {

        LeaveRequestResponse response = leaveService.create(request, callerUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}/decision")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> decide(
            @PathVariable Long id,
            @Valid @RequestBody LeaveDecisionRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(leaveService.decide(id, request, callerUserId)));
    }
}
