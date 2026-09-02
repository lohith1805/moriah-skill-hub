package com.moriah.skillhub.learning;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.learning.dto.CreateVideoLessonRequest;
import com.moriah.skillhub.learning.dto.ModuleSummaryResponse;
import com.moriah.skillhub.learning.dto.MyLessonProgressItem;
import com.moriah.skillhub.learning.dto.RecordLessonProgressRequest;
import com.moriah.skillhub.learning.dto.UpdateVideoLessonRequest;
import com.moriah.skillhub.learning.dto.VideoLessonResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Video Lessons / self-paced learning (gap B1.4). Browsing is open to any authenticated user;
 * curation is {@code DEVELOPER}/{@code TRAINER_PM}/{@code ADMIN}, and edits additionally require
 * creator-or-ADMIN (enforced in {@link LessonService}). Not in {@code SecurityConfig.PUBLIC_PATHS}.
 */
@RestController
@RequestMapping("/api/v1/lessons")
@RequiredArgsConstructor
@Tag(name = "Lessons")
public class LessonController {

    static final String CURATOR_ROLES = "hasAnyRole('DEVELOPER','TRAINER_PM','ADMIN')";

    private final LessonService lessonService;

    @GetMapping
    @Operation(summary = "Browse lessons — optional module filter. Each row carries the caller's progress. "
            + "includeUnpublished is honoured only for curator roles.")
    public ResponseEntity<ApiResponse<PageResponse<VideoLessonResponse>>> list(
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "false") boolean includeUnpublished,
            @CurrentUser Long callerUserId,
            @PageableDefault(size = 20, sort = {"moduleName", "sortOrder"}, direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                lessonService.list(includeUnpublished, module, callerUserId, pageable)));
    }

    @GetMapping("/modules")
    @Operation(summary = "Distinct published module names with their lesson counts")
    public ResponseEntity<ApiResponse<List<ModuleSummaryResponse>>> modules() {
        return ResponseEntity.ok(ApiResponse.success(lessonService.modules()));
    }

    @GetMapping("/me/progress")
    @Operation(summary = "The caller's progress across every lesson they have started")
    public ResponseEntity<ApiResponse<PageResponse<MyLessonProgressItem>>> myProgress(
            @CurrentUser Long callerUserId,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(lessonService.myProgress(callerUserId, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One lesson with the caller's progress")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lesson"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No lesson with this id")
    })
    public ResponseEntity<ApiResponse<VideoLessonResponse>> get(
            @PathVariable Long id, @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(lessonService.get(id, callerUserId)));
    }

    @PostMapping
    @PreAuthorize(CURATOR_ROLES)
    @Operation(summary = "Create a lesson (published defaults to false)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Lesson created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not a curator role")
    })
    public ResponseEntity<ApiResponse<VideoLessonResponse>> create(
            @Valid @RequestBody CreateVideoLessonRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(lessonService.create(request, callerUserId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CURATOR_ROLES)
    @Operation(summary = "Edit a lesson — creator or ADMIN only")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lesson updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the creator and not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No lesson with this id")
    })
    public ResponseEntity<ApiResponse<VideoLessonResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateVideoLessonRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(lessonService.update(id, request, callerUserId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CURATOR_ROLES)
    @Operation(summary = "Unpublish a lesson (is_published = false) — creator or ADMIN only; never row-deletes")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lesson unpublished"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the creator and not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No lesson with this id")
    })
    public ResponseEntity<ApiResponse<Void>> unpublish(@PathVariable Long id, @CurrentUser Long callerUserId) {
        lessonService.unpublish(id, callerUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/progress")
    @Operation(summary = "Report watch progress for the caller — upsert; watchedSeconds never moves backwards")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Progress recorded; lesson returned with updated progress"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No published lesson with this id")
    })
    public ResponseEntity<ApiResponse<VideoLessonResponse>> recordProgress(
            @PathVariable Long id, @Valid @RequestBody RecordLessonProgressRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(lessonService.recordProgress(id, request, callerUserId)));
    }
}
