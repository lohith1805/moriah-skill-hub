package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.hr.dto.CreateLeaveRequest;
import com.moriah.skillhub.hr.dto.LeaveDecisionRequest;
import com.moriah.skillhub.hr.dto.LeaveRequestResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hr/leaves")
@RequiredArgsConstructor
@Tag(name = "HR")
public class LeaveController {

    private final LeaveService leaveService;

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
