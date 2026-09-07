package com.moriah.skillhub.sprint;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.sprint.dto.AssignmentWindowResponse;
import com.moriah.skillhub.sprint.dto.CreateAssignmentWindowRequest;
import com.moriah.skillhub.sprint.dto.CreateSprintRequest;
import com.moriah.skillhub.sprint.dto.SprintResponse;
import com.moriah.skillhub.sprint.dto.UpdateSprintRequest;
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

/** code-standards.md's own canonical {@code SprintController} example, verbatim in shape. Also
 * hosts {@code /api/v1/assignment-windows/**} — same "one controller, per-method paths" precedent
 * feature 09's {@code UserController} established for a feature whose package diagram names only
 * one controller (here, feature 11's diagram names two: {@code SprintController}/{@code
 * TaskController}, neither called out for assignment windows specifically). Every mutation
 * endpoint here is {@code TRAINER_PM}/{@code ADMIN} only, ownership enforced in the service layer
 * (needs the loaded {@code Batch}, same reasoning {@code BatchController}'s own Javadoc gives).
 * Read endpoints are also open to {@code STUDENT} — a deliberate widening beyond
 * code-standards.md's literal PM/ADMIN-only example, because {@code TaskController.pull} needs
 * students to be able to browse {@code BACKLOG} tasks by sprint in the first place; reads carry
 * no ownership/PII risk here the way {@code batch/}'s own {@code list()} already establishes
 * (no per-PM filtering there either). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sprints")
public class SprintController {

    private final SprintService sprintService;
    private final AssignmentWindowService assignmentWindowService;

    @PostMapping("/api/v1/sprints")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Create a sprint")
    public ResponseEntity<ApiResponse<SprintResponse>> create(
            @Valid @RequestBody CreateSprintRequest request, @CurrentUser Long callerUserId) {

        SprintResponse response = sprintService.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/sprints")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN','STUDENT')")
    @Operation(summary = "List sprints for a batch")
    public ResponseEntity<ApiResponse<PageResponse<SprintResponse>>> list(
            @RequestParam Long batchId, @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(sprintService.listByBatch(batchId, pageable)));
    }

    @PutMapping("/api/v1/sprints/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Update a sprint")
    public ResponseEntity<ApiResponse<SprintResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateSprintRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(sprintService.update(callerUserId, id, request)));
    }

    @PostMapping("/api/v1/sprints/{id}/activate")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Activate a sprint (requires the previous sprint COMPLETED)")
    public ResponseEntity<ApiResponse<SprintResponse>> activate(
            @PathVariable Long id, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(sprintService.activate(callerUserId, id)));
    }

    @PostMapping("/api/v1/assignment-windows")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Create a weekly assignment window for a batch")
    public ResponseEntity<ApiResponse<AssignmentWindowResponse>> createAssignmentWindow(
            @Valid @RequestBody CreateAssignmentWindowRequest request, @CurrentUser Long callerUserId) {

        AssignmentWindowResponse response = assignmentWindowService.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/assignment-windows")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN','STUDENT')")
    @Operation(summary = "List assignment windows for a batch")
    public ResponseEntity<ApiResponse<PageResponse<AssignmentWindowResponse>>> listAssignmentWindows(
            @RequestParam Long batchId, @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(assignmentWindowService.listByBatch(batchId, pageable)));
    }
}
