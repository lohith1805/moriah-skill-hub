package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.BaMeetingResponse;
import com.moriah.skillhub.client.dto.CreateBaMeetingRequest;
import com.moriah.skillhub.client.dto.UpdateBaMeetingRequest;
import com.moriah.skillhub.client.entity.BaMeeting;
import com.moriah.skillhub.client.entity.BaMeetingStatus;
import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.repository.BaMeetingRepository;
import com.moriah.skillhub.client.repository.ClientProjectRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaMeetingServiceTest {

    @Mock
    private BaMeetingRepository meetingRepository;
    @Mock
    private ClientProjectRepository clientProjectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

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

    @Test
    void create_withoutProject_savesScheduledMeeting() {
        when(meetingRepository.save(any(BaMeeting.class))).thenAnswer(inv -> {
            BaMeeting m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });
        User creator = new User();
        creator.setId(6L);
        creator.setUuid("ba-uuid");
        when(userRepository.findAllById(List.of(6L))).thenReturn(List.of(creator));

        BaMeetingResponse response = service.create(new CreateBaMeetingRequest(
                "Kickoff", "  ", null, Instant.parse("2026-09-10T09:00:00Z"), 60, "Zoom"), 6L);

        ArgumentCaptor<BaMeeting> captor = ArgumentCaptor.forClass(BaMeeting.class);
        verify(meetingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BaMeetingStatus.SCHEDULED);
        assertThat(captor.getValue().getAgenda()).isNull();
        assertThat(captor.getValue().getClientProject()).isNull();
        assertThat(response.createdByUuid()).isEqualTo("ba-uuid");
    }

    @Test
    void create_withUnknownProject_throwsNotFound() {
        when(clientProjectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateBaMeetingRequest(
                "X", null, 404L, Instant.now(), null, null), 6L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(meetingRepository, never()).save(any());
    }

    @Test
    void update_marksCompletedAndAttachesMinutes() {
        BaMeeting m = meeting(3L);
        ClientProject cp = new ClientProject();
        cp.setId(9L);
        when(meetingRepository.findWithProjectById(3L)).thenReturn(Optional.of(m));
        when(clientProjectRepository.findById(9L)).thenReturn(Optional.of(cp));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        BaMeetingResponse response = service.update(3L, new UpdateBaMeetingRequest(
                "Kickoff (done)", "agenda", 9L, Instant.parse("2026-09-10T09:00:00Z"), 45, "Zoom",
                BaMeetingStatus.COMPLETED, "Agreed on MVP scope."), 6L);

        assertThat(m.getStatus()).isEqualTo(BaMeetingStatus.COMPLETED);
        assertThat(m.getMinutes()).isEqualTo("Agreed on MVP scope.");
        assertThat(m.getClientProject()).isSameAs(cp);
        assertThat(response.clientProjectId()).isEqualTo(9L);
    }

    @Test
    void update_unknownId_throwsNotFound() {
        when(meetingRepository.findWithProjectById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, new UpdateBaMeetingRequest(
                "X", null, null, Instant.now(), null, null, BaMeetingStatus.SCHEDULED, null), 6L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancel_movesToCancelledOnce() {
        BaMeeting m = meeting(3L);
        when(meetingRepository.findWithProjectById(3L)).thenReturn(Optional.of(m));

        service.cancel(3L, 6L);
        assertThat(m.getStatus()).isEqualTo(BaMeetingStatus.CANCELLED);
        verify(auditLogService).record(eq(6L), eq("BA_MEETING_CANCELLED"), any(), any(), any(), any());

        service.cancel(3L, 6L);
        verify(auditLogService).record(eq(6L), eq("BA_MEETING_CANCELLED"), any(), any(), any(), any());
    }
}
