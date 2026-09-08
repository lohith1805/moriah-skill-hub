package com.moriah.skillhub.sprint;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.sprint.dto.AssignTaskRequest;
import com.moriah.skillhub.sprint.dto.CreateTaskRequest;
import com.moriah.skillhub.sprint.dto.TaskResponse;
import com.moriah.skillhub.sprint.dto.UpdateTaskRequest;
import com.moriah.skillhub.sprint.entity.TaskStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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

/** build-plan.md feature 11 "Sensible defaults": mutation endpoints ({@code create}/{@code
 * update}/{@code assign}) are {@code TRAINER_PM}/{@code ADMIN}-gated, ownership enforced in the
 * service layer; {@code pull} is any authenticated {@code STUDENT}, no PM gate, eligibility
 * checked in the service layer instead (batch membership, the PIP hook). Reads are widened to
 * {@code STUDENT} too — see {@code SprintController}'s Javadoc for why. */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Create a task")
    public ResponseEntity<ApiResponse<TaskResponse>> create(
            @Valid @RequestBody CreateTaskRequest request, @CurrentUser Long callerUserId) {

        TaskResponse response = taskService.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN','STUDENT')")
    @Operation(summary = "List tasks for a sprint")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> list(
            @RequestParam Long sprintId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String assignedTo,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(taskService.list(sprintId, status, assignedTo, pageable)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "The caller's own board — tasks assigned to them, plus pullable BACKLOG, across their active batches")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> mine(
            @CurrentUser Long callerUserId, @PageableDefault(size = 100) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(taskService.listMine(callerUserId, pageable)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Update a task, including driving its status forward")
    public ResponseEntity<ApiResponse<TaskResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(taskService.update(callerUserId, id, request)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "PM assigns a BACKLOG task to a specific student")
    public ResponseEntity<ApiResponse<TaskResponse>> assign(
            @PathVariable Long id, @Valid @RequestBody AssignTaskRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(taskService.assign(callerUserId, id, request)));
    }

    @PostMapping("/{id}/pull")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Student self-assigns a BACKLOG task")
    public ResponseEntity<ApiResponse<TaskResponse>> pull(
            @PathVariable Long id, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(taskService.pull(callerUserId, id)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Student moves their own ASSIGNED task to IN_PROGRESS")
    public ResponseEntity<ApiResponse<TaskResponse>> start(
            @PathVariable Long id, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(taskService.start(callerUserId, id)));
    }
}
