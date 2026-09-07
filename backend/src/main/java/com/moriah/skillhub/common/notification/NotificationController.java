package com.moriah.skillhub.common.notification;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.notification.dto.MarkAllReadResponse;
import com.moriah.skillhub.common.notification.dto.NotificationResponse;
import com.moriah.skillhub.common.notification.dto.UnreadCountResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The in-app notifications feed (gap B1.5). Every endpoint is scoped to the authenticated caller
 * via {@code @CurrentUser} — there is no path or query parameter that selects a user, so no
 * {@code @PreAuthorize} beyond the default {@code authenticated()} is needed: any logged-in role
 * may read and clear its own notifications. Not in {@code SecurityConfig.PUBLIC_PATHS}.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationFeedService notificationFeedService;

    @GetMapping
    @Operation(summary = "The caller's in-app notifications, newest first. ?unreadOnly=true limits to unread rows.")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @CurrentUser Long callerUserId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                notificationFeedService.feed(callerUserId, unreadOnly, pageable)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "How many unread in-app notifications the caller has — for a bell badge")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(@CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(notificationFeedService.unreadCount(callerUserId)));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark one notification read — idempotent; 404 if the id is not the caller's own")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Notification marked read"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No such notification for this caller")
    })
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable Long id, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(notificationFeedService.markRead(id, callerUserId)));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark every unread in-app notification read; returns how many were flipped")
    public ResponseEntity<ApiResponse<MarkAllReadResponse>> markAllRead(@CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(notificationFeedService.markAllRead(callerUserId)));
    }
}
