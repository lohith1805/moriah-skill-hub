package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.hr.dto.CreateDisciplinaryActionRequest;
import com.moriah.skillhub.hr.dto.DisciplinaryActionResponse;
import com.moriah.skillhub.hr.dto.UpdateDisciplinaryActionRequest;
import com.moriah.skillhub.hr.entity.DisciplinarySeverity;
import com.moriah.skillhub.hr.entity.DisciplinaryStatus;
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

/** HR Disciplinary (gap B1.10). {@code /api/v1/hr/disciplinary}, HR_MANAGER/ADMIN. */
@RestController
@RequestMapping("/api/v1/hr/disciplinary")
@RequiredArgsConstructor
@Tag(name = "HR")
public class DisciplinaryController {

    private final DisciplinaryService disciplinaryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "Raise a disciplinary action against an employee (starts OPEN)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Action raised"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No employee with this id")
    })
    public ResponseEntity<ApiResponse<DisciplinaryActionResponse>> create(
            @Valid @RequestBody CreateDisciplinaryActionRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(disciplinaryService.create(request, callerUserId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "List disciplinary actions — optional status / severity / employeeId filters")
    public ResponseEntity<ApiResponse<PageResponse<DisciplinaryActionResponse>>> list(
            @RequestParam(required = false) DisciplinaryStatus status,
            @RequestParam(required = false) DisciplinarySeverity severity,
            @RequestParam(required = false) Long employeeId,
            @PageableDefault(size = 20, sort = "incidentDate", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                disciplinaryService.list(status, severity, employeeId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "One disciplinary action by id")
    public ResponseEntity<ApiResponse<DisciplinaryActionResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(disciplinaryService.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "Update an action — status (ACKNOWLEDGED/RESOLVED stamp their timestamps), action taken, resolution notes")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Action updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No disciplinary action with this id")
    })
    public ResponseEntity<ApiResponse<DisciplinaryActionResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateDisciplinaryActionRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(disciplinaryService.update(id, request, callerUserId)));
    }
}
