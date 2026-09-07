package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.hr.dto.EmployeeExitResponse;
import com.moriah.skillhub.hr.dto.InitiateEmployeeExitRequest;
import com.moriah.skillhub.hr.dto.UpdateEmployeeExitRequest;
import com.moriah.skillhub.hr.entity.EmployeeExitStatus;
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

/**
 * HR Exit Management (gap B1.10). {@code /api/v1/hr/exits}, HR_MANAGER/ADMIN — the {@code /hr/**}
 * role-gating convention. {@code complete} is split from {@code update} because only it has a
 * side effect on the {@code employees} row.
 */
@RestController
@RequestMapping("/api/v1/hr/exits")
@RequiredArgsConstructor
@Tag(name = "HR")
public class EmployeeExitController {

    private final EmployeeExitService employeeExitService;

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "Initiate an employee's offboarding (starts INITIATED)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Exit initiated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No employee with this id"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "The employee already has an open exit record")
    })
    public ResponseEntity<ApiResponse<EmployeeExitResponse>> initiate(
            @Valid @RequestBody InitiateEmployeeExitRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(employeeExitService.initiate(request, callerUserId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "List exit records — optional status / employeeId filters")
    public ResponseEntity<ApiResponse<PageResponse<EmployeeExitResponse>>> list(
            @RequestParam(required = false) EmployeeExitStatus status,
            @RequestParam(required = false) Long employeeId,
            @PageableDefault(size = 20, sort = "lastWorkingDay", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(employeeExitService.list(status, employeeId, pageable)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "Update an in-progress exit — checklist, notes, working status. Not for COMPLETED.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exit updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "status was COMPLETED (use /complete)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No exit record with this id"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "The exit is already completed")
    })
    public ResponseEntity<ApiResponse<EmployeeExitResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateEmployeeExitRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(employeeExitService.update(id, request, callerUserId)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "Finalise an exit — sets the employee to EXITED (or TERMINATED) and stamps date_of_exit")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exit completed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No exit record with this id"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "The exit is not in an open status")
    })
    public ResponseEntity<ApiResponse<EmployeeExitResponse>> complete(
            @PathVariable Long id, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(employeeExitService.complete(id, callerUserId)));
    }
}
