package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.hr.dto.CreateOnboardingRequest;
import com.moriah.skillhub.hr.dto.EmployeeOnboardingResponse;
import com.moriah.skillhub.hr.dto.MyOnboardingStatusResponse;
import com.moriah.skillhub.hr.dto.UpdateOnboardingRequest;
import com.moriah.skillhub.hr.entity.OnboardingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

/** HR Onboarding (gap B1.10). {@code /api/v1/hr/onboardings}, HR_MANAGER/ADMIN. */
@RestController
@RequestMapping("/api/v1/hr/onboardings")
@RequiredArgsConstructor
@Tag(name = "HR")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/my-status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "The caller's own provisioning state, for the dashboard access gate")
    public ResponseEntity<ApiResponse<MyOnboardingStatusResponse>> myStatus(@CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(onboardingService.myStatus(callerUserId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "Start an employee's onboarding (starts NOT_STARTED)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Onboarding created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No employee with this id, or buddyUuid does not exist"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "The employee already has an open onboarding record")
    })
    public ResponseEntity<ApiResponse<EmployeeOnboardingResponse>> create(
            @Valid @RequestBody CreateOnboardingRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(onboardingService.create(request, callerUserId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "List onboarding records — optional status / employeeId filters")
    public ResponseEntity<ApiResponse<PageResponse<EmployeeOnboardingResponse>>> list(
            @RequestParam(required = false) OnboardingStatus status,
            @RequestParam(required = false) Long employeeId,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(onboardingService.list(status, employeeId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "One onboarding record by id")
    public ResponseEntity<ApiResponse<EmployeeOnboardingResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(onboardingService.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "Update an onboarding — checklist, buddy, notes, status (COMPLETED stamps completedAt)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Onboarding updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No onboarding record with this id, or buddyUuid does not exist")
    })
    public ResponseEntity<ApiResponse<EmployeeOnboardingResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateOnboardingRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(onboardingService.update(id, request, callerUserId)));
    }
}
