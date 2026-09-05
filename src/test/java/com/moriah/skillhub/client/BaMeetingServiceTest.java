package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.BaMeetingResponse;
import com.moriah.skillhub.client.dto.CreateBaMeetingRequest;
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
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserStatus;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Client Pre-Project Discussions" (the frontend's own name for BA meetings, gap B1.14) — the
 * user's own redesign added named attendees: a role -> employee checkbox picker where each
 * checked person is emailed (+ an IN_APP row) and sees the meeting under {@code GET
 * /api/v1/meetings/my} regardless of who scheduled it. */
@ExtendWith(MockitoExtension.class)
class BaMeetingServiceTest {

    @Mock
    private BaMeetingRepository meetingRepository;
    @Mock
    private BaMeetingAttendeeRepository attendeeRepository;
    @Mock
    private ClientProjectRepository clientProjectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BaMeetingService service;

    private BaMeeting meeting(long id) {
        BaMeeting m = new BaMeeting();
        m.setId(id);
        m.setTitle("Kickoff");
        m.setScheduledAt(Instant.parse("2026-09-10T09:00:00Z"));
        m.setStatus(BaMeetingStatus.SCHEDULED);
        m.setCreatedBy(6L);
        return m;
    }

    private User user(long id, String uuid, String email) {
        User u = new User();
        u.setId(id);
        u.setUuid(uuid);
        u.setFullName("User " + id);
        u.setEmail(email);
        return u;
    }

    @Test
    void create_withoutProjectOrAttendees_savesScheduledMeeting() {
        when(meetingRepository.save(any(BaMeeting.class))).thenAnswer(inv -> {
            BaMeeting m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });
        User creator = user(6L, "ba-uuid", "ba@example.com");
        when(userRepository.findAllById(List.of(6L))).thenReturn(List.of(creator));

        BaMeetingResponse response = service.create(new CreateBaMeetingRequest(
                "Kickoff", "  ", null, Instant.parse("2026-09-10T09:00:00Z"), 60, "Zoom", null), 6L);

        ArgumentCaptor<BaMeeting> captor = ArgumentCaptor.forClass(BaMeeting.class);
        verify(meetingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BaMeetingStatus.SCHEDULED);
        assertThat(captor.getValue().getAgenda()).isNull();
        assertThat(captor.getValue().getClientProject()).isNull();
        assertThat(response.createdByUuid()).isEqualTo("ba-uuid");
        assertThat(response.attendees()).isEmpty();
        verify(attendeeRepository, never()).save(any());
        verify(notificationService, never()).enqueueAfterCommit(any(), any(), any(), any());
    }

    @Test
    void create_withUnknownProject_throwsNotFound() {
        when(clientProjectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateBaMeetingRequest(
                "X", null, 404L, Instant.now(), null, null, null), 6L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(meetingRepository, never()).save(any());
    }

    @Test
    void create_withAttendees_savesAttendeeRowsAndNotifiesEachExceptTheCaller() {
        when(meetingRepository.save(any(BaMeeting.class))).thenAnswer(inv -> {
            BaMeeting m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });
        User caller = user(6L, "ba-uuid", "ba@example.com");
        User dev = user(7L, "dev-uuid", "dev@example.com");
        when(userRepository.findAllById(List.of(6L))).thenReturn(List.of(caller));
        when(userRepository.findByUuid("ba-uuid")).thenReturn(Optional.of(caller));
        when(userRepository.findByUuid("dev-uuid")).thenReturn(Optional.of(dev));
        when(userRepository.findAllById(java.util.Set.of(6L, 7L))).thenReturn(List.of(caller, dev));

        BaMeetingResponse response = service.create(new CreateBaMeetingRequest(
                "Kickoff", null, null, Instant.parse("2026-09-10T09:00:00Z"), 60, "Zoom",
                List.of("ba-uuid", "dev-uuid")), 6L);

        assertThat(response.attendees()).extracting(a -> a.uuid()).containsExactlyInAnyOrder("ba-uuid", "dev-uuid");
        verify(attendeeRepository, times(2)).save(any(BaMeetingAttendee.class));
        // Caller invited themselves along with dev — only dev gets emailed/notified.
        verify(notificationService).enqueueAfterCommit(eq(7L), eq(NotificationChannel.EMAIL), eq("MEETING_INVITE"), any());
        verify(notificationService).enqueueAfterCommit(eq(7L), eq(NotificationChannel.IN_APP), eq("MEETING_INVITE"), any());
        verify(notificationService, never()).enqueueAfterCommit(eq(6L), any(), any(), any());
    }

    @Test
    void create_withUnknownAttendeeUuid_throwsUserNotFound() {
        when(meetingRepository.save(any(BaMeeting.class))).thenAnswer(inv -> {
            BaMeeting m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });
        when(userRepository.findByUuid("ghost-uuid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateBaMeetingRequest(
                "Kickoff", null, null, Instant.now(), null, null, List.of("ghost-uuid")), 6L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void update_marksCompletedAndAttachesMinutes() {
        BaMeeting m = meeting(3L);
        ClientProject cp = new ClientProject();
        cp.setId(9L);
        when(meetingRepository.findWithProjectById(3L)).thenReturn(Optional.of(m));
        when(clientProjectRepository.findById(9L)).thenReturn(Optional.of(cp));
        when(attendeeRepository.findByIdMeetingId(3L)).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        BaMeetingResponse response = service.update(3L, new UpdateBaMeetingRequest(
                "Kickoff (done)", "agenda", 9L, Instant.parse("2026-09-10T09:00:00Z"), 45, "Zoom",
                BaMeetingStatus.COMPLETED, "Agreed on MVP scope.", null), 6L);

        assertThat(m.getStatus()).isEqualTo(BaMeetingStatus.COMPLETED);
        assertThat(m.getMinutes()).isEqualTo("Agreed on MVP scope.");
        assertThat(m.getClientProject()).isSameAs(cp);
        assertThat(response.clientProjectId()).isEqualTo(9L);
        // null attendeeUuids -> invite list left untouched.
        verify(attendeeRepository, never()).deleteByIdMeetingId(any());
    }

    @Test
    void update_withAttendeeUuids_replacesWholesaleAndNotifiesOnlyNewlyAdded() {
        BaMeeting m = meeting(3L);
        User alreadyInvited = user(7L, "dev-uuid", "dev@example.com");
        User newlyAdded = user(8L, "ba2-uuid", "ba2@example.com");
        when(meetingRepository.findWithProjectById(3L)).thenReturn(Optional.of(m));
        when(attendeeRepository.findByIdMeetingId(3L))
                .thenReturn(List.of(new BaMeetingAttendee(3L, 7L)));
        when(userRepository.findByUuid("dev-uuid")).thenReturn(Optional.of(alreadyInvited));
        when(userRepository.findByUuid("ba2-uuid")).thenReturn(Optional.of(newlyAdded));
        when(userRepository.findAllById(java.util.Set.of(7L, 8L))).thenReturn(List.of(alreadyInvited, newlyAdded));
        when(userRepository.findAllById(List.of(6L))).thenReturn(List.of()); // resolveCreatorUuids

        service.update(3L, new UpdateBaMeetingRequest(
                "Kickoff", null, null, Instant.parse("2026-09-10T09:00:00Z"), 60, "Zoom",
                BaMeetingStatus.SCHEDULED, null, List.of("dev-uuid", "ba2-uuid")), 6L);

        verify(attendeeRepository).deleteByIdMeetingId(3L);
        verify(attendeeRepository, times(2)).save(any(BaMeetingAttendee.class));
        verify(notificationService).enqueueAfterCommit(eq(8L), eq(NotificationChannel.EMAIL), eq("MEETING_INVITE"), any());
        verify(notificationService, never()).enqueueAfterCommit(eq(7L), any(), any(), any());
    }

    @Test
    void update_unknownId_throwsNotFound() {
        when(meetingRepository.findWithProjectById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, new UpdateBaMeetingRequest(
                "X", null, null, Instant.now(), null, null, BaMeetingStatus.SCHEDULED, null, null), 6L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancel_movesToCancelledOnceAndNotifiesRemainingAttendees() {
        BaMeeting m = meeting(3L);
        User dev = user(7L, "dev-uuid", "dev@example.com");
        when(meetingRepository.findWithProjectById(3L)).thenReturn(Optional.of(m));
        when(attendeeRepository.findUserIdsByMeetingId(3L)).thenReturn(List.of(7L));
        when(userRepository.findAllById(List.of(7L))).thenReturn(List.of(dev));

        service.cancel(3L, 6L);
        assertThat(m.getStatus()).isEqualTo(BaMeetingStatus.CANCELLED);
        verify(auditLogService).record(eq(6L), eq("BA_MEETING_CANCELLED"), any(), any(), any(), any());
        verify(notificationService).enqueueAfterCommit(eq(7L), eq(NotificationChannel.EMAIL), eq("MEETING_CANCELLED"), any());

        service.cancel(3L, 6L);
        verify(auditLogService).record(eq(6L), eq("BA_MEETING_CANCELLED"), any(), any(), any(), any());
    }

    @Test
    void myMeetings_returnsOnlyMeetingsTheCallerIsInvitedTo() {
        when(attendeeRepository.findByIdUserId(7L)).thenReturn(List.of(new BaMeetingAttendee(3L, 7L)));
        BaMeeting m = meeting(3L);
        when(meetingRepository.findByIdIn(List.of(3L), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(m)));
        when(userRepository.findAllById(List.of(6L))).thenReturn(List.of());
        when(attendeeRepository.findByIdMeetingIdIn(List.of(3L))).thenReturn(List.of());

        var page = service.myMeetings(7L, Pageable.unpaged());

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).id()).isEqualTo(3L);
    }

    @Test
    void myMeetings_noInvites_returnsEmptyPageWithoutQueryingMeetings() {
        when(attendeeRepository.findByIdUserId(7L)).thenReturn(List.of());

        var page = service.myMeetings(7L, PageRequest.of(0, 20));

        assertThat(page.content()).isEmpty();
        verify(meetingRepository, never()).findByIdIn(any(), any());
    }

    @Test
    void staffDirectory_invitableRole_returnsActiveUsers() {
        User dev = user(7L, "dev-uuid", "dev@example.com");
        when(userRepository.search(eq(UserStatus.ACTIVE), eq(RoleCode.DEVELOPER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dev)));

        List<StaffDirectoryEntryResponse> result = service.staffDirectory(RoleCode.DEVELOPER);

        assertThat(result).extracting(StaffDirectoryEntryResponse::uuid).containsExactly("dev-uuid");
    }

    @Test
    void staffDirectory_nonInvitableRole_throwsValidationFailed() {
        assertThatThrownBy(() -> service.staffDirectory(RoleCode.STUDENT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }
}
