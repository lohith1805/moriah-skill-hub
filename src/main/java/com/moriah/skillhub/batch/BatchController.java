package com.moriah.skillhub.batch;

import com.moriah.skillhub.batch.dto.AddStudentRequest;
import com.moriah.skillhub.batch.dto.BatchResponse;
import com.moriah.skillhub.batch.dto.CreateBatchRequest;
import com.moriah.skillhub.batch.dto.UpdateBatchRequest;
import com.moriah.skillhub.certificate.GraduationService;
import com.moriah.skillhub.certificate.dto.GraduationResponse;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.user.entity.RoleCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mutations ({@code create}/{@code update}/{@code addStudent}/{@code removeStudent}/{@code
 * graduate}) are {@code TRAINER_PM}/{@code ADMIN} only, with per-batch ownership enforced in
 * {@link BatchService} (it needs the loaded {@code Batch}, which a static {@code @PreAuthorize}
 * can't check). The two reads ({@code list}, {@code get}) are widened to {@code STUDENT} —
 * matching {@code SprintController}/{@code TaskController} and the published API doc — but a
 * STUDENT-only caller sees only the batches they are enrolled in. */
@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
@Tag(name = "Batches")
public class BatchController {

    private final BatchService batchService;
    private final GraduationService graduationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Create a batch — the caller becomes its PM")
    public ResponseEntity<ApiResponse<BatchResponse>> create(
            @Valid @RequestBody CreateBatchRequest request, @CurrentUser Long callerUserId) {

        BatchResponse response = batchService.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN','STUDENT')")
    @Operation(summary = "List batches — a STUDENT sees only the batches they are enrolled in")
    public ResponseEntity<ApiResponse<PageResponse<BatchResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable, @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(batchService.list(callerUserId, isStudentOnly(), pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN','STUDENT')")
    @Operation(summary = "Get one batch — a STUDENT may only read a batch they are enrolled in")
    public ResponseEntity<ApiResponse<BatchResponse>> get(@PathVariable Long id, @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(batchService.get(id, callerUserId, isStudentOnly())));
    }

    /** A caller who holds STUDENT and nothing that grants the full list (ADMIN / TRAINER_PM). */
    private static boolean isStudentOnly() {
        var roles = SecurityUtils.currentUserRoles();
        return !roles.contains(RoleCode.ADMIN.name()) && !roles.contains(RoleCode.TRAINER_PM.name());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Update a batch")
    public ResponseEntity<ApiResponse<BatchResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateBatchRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(batchService.update(callerUserId, id, request)));
    }

    @PostMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Manually add a student to a batch")
    public ResponseEntity<ApiResponse<BatchResponse>> addStudent(
            @PathVariable Long id, @Valid @RequestBody AddStudentRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(batchService.addStudent(callerUserId, id, request)));
    }

    /** {@code {userUuid}}, not {@code {userId}} — build-plan.md's endpoint list names the path
     * variable {@code userId}, but the public identifier for a user is always {@code uuid}
     * everywhere else in this project (User.java's own invariant); the placeholder name in the
     * spec is illustrative shorthand, not a literal contract. */
    @DeleteMapping("/{id}/students/{userUuid}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Remove a student from a batch (sets status to REASSIGNED, never deletes)")
    public ResponseEntity<ApiResponse<Void>> removeStudent(
            @PathVariable Long id, @PathVariable String userUuid, @CurrentUser Long callerUserId) {

        batchService.removeStudent(callerUserId, id, userUuid);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** {@code {userUuid}}, not build-plan.md's literal {@code {userId}} — same precedent as
     * {@link #removeStudent}'s own Javadoc above. build-plan.md feature 20: "Graduation sign-off
     * ... Nothing else in the system sets this status, and certificate issuance requires it."
     * Delegates to {@code certificate.GraduationService}, not {@code BatchService} directly — the
     * audit-log write and response assembly are that service's job (architecture.md package
     * diagram: {@code GraduationService} lives in {@code certificate/}), even though the HTTP path
     * itself is a batch-management concern and so stays on this controller. */
    @PostMapping("/{id}/students/{userUuid}/graduate")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Graduate an ACTIVE student — required before a certificate can be issued")
    public ResponseEntity<ApiResponse<GraduationResponse>> graduate(
            @PathVariable Long id, @PathVariable String userUuid, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(graduationService.graduate(callerUserId, id, userUuid)));
    }
}
