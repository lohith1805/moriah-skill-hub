package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.BaMeetingResponse;
import com.moriah.skillhub.client.dto.CreateBaMeetingRequest;
import com.moriah.skillhub.client.dto.MeetingAttendeeResponse;
import com.moriah.skillhub.client.dto.StaffDirectoryEntryResponse;
import com.moriah.skillhub.client.dto.UpdateBaMeetingRequest;
import com.moriah.skillhub.client.entity.BaMeeting;
import com.moriah.skillhub.client.entity.BaMeetingAttendee;
import com.moriah.skillhub.client.entity.BaMeetingStatus;
import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.repository.BaMeetingAttendeeRepository;
import com.moriah.skillhub.client.repository.BaMeetingRepository;
import com.moriah.skillhub.client.repository.ClientProjectRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserStatus;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BA Meetings coordination (gap B1.14) — "Client Pre-Project Discussions" in the frontend. CRUD
 * for {@code ba_meetings}, BUSINESS_ANALYST/ADMIN (gated on {@link BaMeetingController}). Named
 * attendees ({@link BaMeetingAttendee}) are the user's own redesign: a role -> employee picker
 * where each checked person is emailed the invite and sees the meeting on their own dashboard via
 * {@link #myMeetings} — a shared, role-agnostic read path any attendee can hit regardless of
 * whether they hold BUSINESS_ANALYST.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BaMeetingService {

    private static final DateTimeFormatter INVITE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' h:mm a 'UTC'").withZone(ZoneOffset.UTC);

    /** The cast the user's own description named ("roles like developers, BA") plus ADMIN for
     * oversight and CLIENT so the project's own client contact can actually be invited to their
     * own kickoff — deliberately not every {@link RoleCode} (STUDENT/HR_MANAGER/LEAD_GEN/
     * TRAINER_PM have no reason to sit in on a client discussion). Picking the right CLIENT out
     * of the roster is on the BA (same trust as picking the right Developer) — this directory is
     * role-wide, not scoped to any one client project. */
    private static final Set<RoleCode> INVITABLE_DIRECTORY_ROLES =
            Set.of(RoleCode.BUSINESS_ANALYST, RoleCode.DEVELOPER, RoleCode.ADMIN, RoleCode.CLIENT);

    private final BaMeetingRepository meetingRepository;
    private final BaMeetingAttendeeRepository attendeeRepository;
    private final ClientProjectRepository clientProjectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<BaMeetingResponse> list(BaMeetingStatus status, Long clientProjectId, Pageable pageable) {
        var page = meetingRepository.search(status, clientProjectId, pageable);
        Map<Long, String> creatorUuids = resolveCreatorUuids(page.getContent());
        Map<Long, List<BaMeetingAttendee>> attendeesByMeeting = batchAttendees(page.getContent());
        return PageResponse.from(page.map(m -> toResponse(m, creatorUuids, attendeesByMeeting.getOrDefault(m.getId(), List.of()))));
    }

    /** {@code GET /api/v1/meetings/my} — every meeting the caller is invited to, regardless of
     * who scheduled it or which role they hold; powers "Client Pre-Project Discussions" on the
     * invitee's own dashboard (not just the BA portal). */
    @Transactional(readOnly = true)
    public PageResponse<BaMeetingResponse> myMeetings(Long callerUserId, Pageable pageable) {
        List<Long> meetingIds = attendeeRepository.findByIdUserId(callerUserId).stream()
                .map(a -> a.getId().getMeetingId())
                .toList();
        if (meetingIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }
        var page = meetingRepository.findByIdIn(meetingIds, pageable);
        Map<Long, String> creatorUuids = resolveCreatorUuids(page.getContent());
        Map<Long, List<BaMeetingAttendee>> attendeesByMeeting = batchAttendees(page.getContent());
        return PageResponse.from(page.map(m -> toResponse(m, creatorUuids, attendeesByMeeting.getOrDefault(m.getId(), List.of()))));
    }

    /** {@code GET /api/v1/ba/meetings/staff-directory?role=} — the role -> employee cascading
     * picker's second dropdown: every active user holding {@code role}, checkbox-selectable. */
    @Transactional(readOnly = true)
    public List<StaffDirectoryEntryResponse> staffDirectory(RoleCode role) {
        requireInvitableRole(role);
        return userRepository.search(UserStatus.ACTIVE, role, Pageable.unpaged()).getContent().stream()
                .map(u -> new StaffDirectoryEntryResponse(u.getUuid(), u.getFullName(), u.getEmail()))
                .toList();
    }

    @Transactional
    public BaMeetingResponse create(CreateBaMeetingRequest request, Long callerUserId) {
        BaMeeting meeting = new BaMeeting();
        meeting.setTitle(request.title());
        meeting.setAgenda(blankToNull(request.agenda()));
        meeting.setClientProject(resolveProject(request.clientProjectId()));
        meeting.setScheduledAt(request.scheduledAt());
        meeting.setDurationMinutes(request.durationMinutes());
        meeting.setLocation(blankToNull(request.location()));
        meeting.setStatus(BaMeetingStatus.SCHEDULED);
        meeting.setCreatedBy(callerUserId);
        meetingRepository.save(meeting);

        List<User> attendees = resolveAttendees(request.attendeeUuids());
        saveAttendees(meeting.getId(), attendees);
        notifyInvited(meeting, attendees, callerUserId);

        auditLogService.record(callerUserId, "BA_MEETING_CREATED", "BaMeeting", meeting.getId(), null, meeting.getTitle());
        log.info("[ba/meetings] {} scheduled meeting {} with {} attendee(s)", callerUserId, meeting.getId(), attendees.size());
        return toResponse(meeting, resolveCreatorUuids(List.of(meeting)), toAttendeeRows(meeting.getId(), attendees));
    }

    @Transactional
    public BaMeetingResponse update(Long id, UpdateBaMeetingRequest request, Long callerUserId) {
        BaMeeting meeting = requireMeeting(id);

        meeting.setTitle(request.title());
        meeting.setAgenda(blankToNull(request.agenda()));
        meeting.setClientProject(resolveProject(request.clientProjectId()));
        meeting.setScheduledAt(request.scheduledAt());
        meeting.setDurationMinutes(request.durationMinutes());
        meeting.setLocation(blankToNull(request.location()));
        meeting.setStatus(request.status());
        meeting.setMinutes(blankToNull(request.minutes()));

        List<BaMeetingAttendee> currentAttendees = attendeeRepository.findByIdMeetingId(id);
        if (request.attendeeUuids() == null) {
            // Leave the invite list untouched.
            auditLogService.record(callerUserId, "BA_MEETING_UPDATED", "BaMeeting", meeting.getId(), null, meeting.getStatus());
            List<User> unchanged = userRepository.findAllById(
                    currentAttendees.stream().map(a -> a.getId().getUserId()).toList());
            return toResponse(meeting, resolveCreatorUuids(List.of(meeting)), toAttendeeRows(id, unchanged));
        }

        List<User> newAttendees = resolveAttendees(request.attendeeUuids());
        Set<Long> currentUserIds = currentAttendees.stream().map(a -> a.getId().getUserId()).collect(Collectors.toSet());
        List<User> newlyAdded = newAttendees.stream().filter(u -> !currentUserIds.contains(u.getId())).toList();

        attendeeRepository.deleteByIdMeetingId(id);
        saveAttendees(id, newAttendees);
        notifyInvited(meeting, newlyAdded, callerUserId);

        auditLogService.record(callerUserId, "BA_MEETING_UPDATED", "BaMeeting", meeting.getId(), null, meeting.getStatus());
        log.info("[ba/meetings] {} updated meeting {} — {} newly invited", callerUserId, meeting.getId(), newlyAdded.size());
        return toResponse(meeting, resolveCreatorUuids(List.of(meeting)), toAttendeeRows(id, newAttendees));
    }

    /** {@code DELETE} — moves to {@code CANCELLED}, never row-deletes. Idempotent. Notifies every
     * still-invited attendee so nobody shows up to a meeting that no longer exists. */
    @Transactional
    public void cancel(Long id, Long callerUserId) {
        BaMeeting meeting = requireMeeting(id);
        if (meeting.getStatus() != BaMeetingStatus.CANCELLED) {
            meeting.setStatus(BaMeetingStatus.CANCELLED);
            auditLogService.record(callerUserId, "BA_MEETING_CANCELLED", "BaMeeting", meeting.getId(), null, meeting.getTitle());

            List<Long> attendeeUserIds = attendeeRepository.findUserIdsByMeetingId(id);
            userRepository.findAllById(attendeeUserIds).forEach(u -> {
                if (!u.getId().equals(callerUserId)) {
                    notificationService.enqueueAfterCommit(u.getId(), NotificationChannel.EMAIL, "MEETING_CANCELLED", Map.of(
                            "to", u.getEmail(),
                            "subject", "Cancelled: " + meeting.getTitle(),
                            "body", "The Client Pre-Project Discussion \"" + meeting.getTitle() + "\" scheduled for "
                                    + INVITE_TIME_FORMAT.format(meeting.getScheduledAt()) + " has been cancelled."));
                }
            });
        }
    }

    /** {@code POST /api/v1/meetings/{id}/note} — the user's own ask: once a discussion is over,
     * any attendee (not just the BA who scheduled it) can jot down a small note about what it
     * covered — not gated to BUSINESS_ANALYST/ADMIN the way {@link #update}'s full-field
     * {@code minutes} write is. Requires the meeting to actually be {@code COMPLETED} (nothing to
     * summarize about a meeting that hasn't happened yet) and the caller to be either its creator
     * or one of its named attendees — an ADMIN may always write one, matching this codebase's
     * "ADMIN bypasses ownership" convention elsewhere. */
    @Transactional
    public BaMeetingResponse addNote(Long id, Long callerUserId, String note) {
        BaMeeting meeting = requireMeeting(id);
        if (meeting.getStatus() != BaMeetingStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This discussion isn't over yet — a note can only be added once it's completed.");
        }

        boolean isAdmin = com.moriah.skillhub.common.security.SecurityUtils.currentUserRoles()
                .contains(RoleCode.ADMIN.name());
        boolean isCreator = meeting.getCreatedBy().equals(callerUserId);
        boolean isAttendee = attendeeRepository.findUserIdsByMeetingId(id).contains(callerUserId);
        if (!isAdmin && !isCreator && !isAttendee) {
            throw new com.moriah.skillhub.common.exception.ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        meeting.setMinutes(note);
        auditLogService.record(callerUserId, "BA_MEETING_NOTE_ADDED", "BaMeeting", meeting.getId(), null, meeting.getTitle());
        log.info("[meetings] {} added a note to completed meeting {}", callerUserId, meeting.getId());

        List<BaMeetingAttendee> attendeeRows = attendeeRepository.findByIdMeetingId(id);
        return toResponse(meeting, resolveCreatorUuids(List.of(meeting)), attendeeRows);
    }

    private void requireInvitableRole(RoleCode role) {
        if (!INVITABLE_DIRECTORY_ROLES.contains(role)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "role must be one of " + INVITABLE_DIRECTORY_ROLES);
        }
    }

    /** Validates every uuid resolves to a real user — a typo'd uuid fails the whole request rather
     * than silently inviting nobody. {@code null}/empty input means "no attendees," not an error. */
    private List<User> resolveAttendees(List<String> attendeeUuids) {
        if (attendeeUuids == null || attendeeUuids.isEmpty()) {
            return List.of();
        }
        // LinkedHashSet: de-dupe a repeated uuid while keeping the caller's own ordering.
        List<User> resolved = new ArrayList<>();
        for (String uuid : new LinkedHashSet<>(attendeeUuids)) {
            resolved.add(userRepository.findByUuid(uuid)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, uuid)));
        }
        return resolved;
    }

    private void saveAttendees(Long meetingId, List<User> attendees) {
        attendees.forEach(u -> attendeeRepository.save(new BaMeetingAttendee(meetingId, u.getId())));
    }

    private List<BaMeetingAttendee> toAttendeeRows(Long meetingId, List<User> attendees) {
        return attendees.stream().map(u -> new BaMeetingAttendee(meetingId, u.getId())).toList();
    }

    /** Every major thing gets emailed (the user's own instruction) — plus an IN_APP row so it
     * shows up in the invitee's notification feed immediately, not just in their "Client
     * Pre-Project Discussions" list. Skips the caller themselves — scheduling your own meeting
     * doesn't need a self-invite email. */
    private void notifyInvited(BaMeeting meeting, List<User> attendees, Long callerUserId) {
        String when = INVITE_TIME_FORMAT.format(meeting.getScheduledAt());
        String where = meeting.getLocation() != null ? meeting.getLocation() : "location to be shared";
        for (User attendee : attendees) {
            if (attendee.getId().equals(callerUserId)) {
                continue;
            }
            String body = "You're invited to a Client Pre-Project Discussion: \"" + meeting.getTitle() + "\" on "
                    + when + ". Location/link: " + where
                    + (meeting.getAgenda() != null ? (". Agenda: " + meeting.getAgenda()) : "");
            notificationService.enqueueAfterCommit(attendee.getId(), NotificationChannel.EMAIL, "MEETING_INVITE", Map.of(
                    "to", attendee.getEmail(),
                    "subject", "You're invited: " + meeting.getTitle(),
                    "body", body));
            notificationService.enqueueAfterCommit(attendee.getId(), NotificationChannel.IN_APP, "MEETING_INVITE", Map.of(
                    "title", "Client Pre-Project Discussion invite",
                    "body", "\"" + meeting.getTitle() + "\" — " + when));
        }
    }

    private ClientProject resolveProject(Long clientProjectId) {
        if (clientProjectId == null) {
            return null;
        }
        return clientProjectRepository.findById(clientProjectId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLIENT_PROJECT_NOT_FOUND, clientProjectId));
    }

    private BaMeeting requireMeeting(Long id) {
        return meetingRepository.findWithProjectById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, id));
    }

    private Map<Long, String> resolveCreatorUuids(List<BaMeeting> meetings) {
        List<Long> ids = meetings.stream().map(BaMeeting::getCreatedBy).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getUuid));
    }

    /** {@code list}/{@code myMeetings}'s batched per-page attendee lookup — one flat query for a
     * whole page instead of one {@code findByIdMeetingId} call per row. */
    private Map<Long, List<BaMeetingAttendee>> batchAttendees(List<BaMeeting> meetings) {
        if (meetings.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = meetings.stream().map(BaMeeting::getId).toList();
        return attendeeRepository.findByIdMeetingIdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getId().getMeetingId()));
    }

    private BaMeetingResponse toResponse(BaMeeting m, Map<Long, String> creatorUuids, List<BaMeetingAttendee> attendeeRows) {
        List<Long> attendeeUserIds = attendeeRows.stream().map(a -> a.getId().getUserId()).toList();
        Map<Long, User> usersById = attendeeUserIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(new HashSet<>(attendeeUserIds)).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        List<MeetingAttendeeResponse> attendees = attendeeUserIds.stream()
                .map(usersById::get)
                .filter(java.util.Objects::nonNull)
                .map(u -> new MeetingAttendeeResponse(u.getUuid(), u.getFullName(), u.getEmail()))
                .toList();

        return new BaMeetingResponse(
                m.getId(), m.getTitle(), m.getAgenda(),
                m.getClientProject() == null ? null : m.getClientProject().getId(),
                m.getScheduledAt(), m.getDurationMinutes(), m.getLocation(),
                m.getStatus(), m.getMinutes(), creatorUuids.get(m.getCreatedBy()),
                m.getCreatedAt(), m.getUpdatedAt(), attendees);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
