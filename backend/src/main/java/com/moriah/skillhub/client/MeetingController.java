package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.AddMeetingNoteRequest;
import com.moriah.skillhub.client.dto.BaMeetingResponse;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shared "Client Pre-Project Discussions" surface for an <em>invitee</em> — as opposed to {@link
 * BaMeetingController}, which is the scheduler's (BUSINESS_ANALYST/ADMIN) own CRUD view.
 * BUSINESS_ANALYST/DEVELOPER/ADMIN/CLIENT are exactly the roles {@code
 * BaMeetingService#staffDirectory} lets a BA invite in the first place, so those are exactly who
 * this needs to serve — a developer (or the client themselves) invited to a discussion sees it
 * here on their own dashboard, without needing any BA-only role.
 */
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
@Tag(name = "Meetings")
public class MeetingController {

    private final BaMeetingService baMeetingService;

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('BUSINESS_ANALYST','DEVELOPER','ADMIN','CLIENT')")
    @Operation(summary = "Client Pre-Project Discussions the caller is invited to",
            description = "Every meeting where the caller is a named attendee, regardless of who scheduled it")
    public ResponseEntity<ApiResponse<PageResponse<BaMeetingResponse>>> myMeetings(
            @CurrentUser Long callerUserId,
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(baMeetingService.myMeetings(callerUserId, pageable)));
    }

    @PostMapping("/{id}/note")
    @PreAuthorize("hasAnyRole('BUSINESS_ANALYST','DEVELOPER','ADMIN','CLIENT')")
    @Operation(summary = "Add a small note to a completed discussion",
            description = "Any named attendee (or the scheduler) may write one, once the meeting is COMPLETED — "
                    + "409 if it isn't over yet, 403 if the caller wasn't part of it")
    public ResponseEntity<ApiResponse<BaMeetingResponse>> addNote(
            @PathVariable Long id, @Valid @RequestBody AddMeetingNoteRequest request, @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(baMeetingService.addNote(id, callerUserId, request.note())));
    }
}
