package com.moriah.skillhub.common.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.notification.dto.NotificationResponse;
import com.moriah.skillhub.common.notification.entity.Notification;
import com.moriah.skillhub.common.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationFeedServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private NotificationFeedService service;

    private Notification inApp(long id, String payloadJson, Instant readAt) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(7L);
        n.setChannel(NotificationChannel.IN_APP);
        n.setTemplateCode("PIP_OPENED");
        n.setPayload(payloadJson);
        n.setReadAt(readAt);
        return n;
    }

    @Test
    void feed_deserializesPayloadAndFlagsReadState() {
        Notification row = inApp(1L, "{\"batchId\":42,\"title\":\"You are on a PIP\"}", null);
        when(notificationRepository.findByUserIdAndChannel(eq(7L), eq(NotificationChannel.IN_APP), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        PageResponse<NotificationResponse> page = service.feed(7L, false, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        NotificationResponse dto = page.content().get(0);
        assertThat(dto.templateCode()).isEqualTo("PIP_OPENED");
        assertThat(dto.payload()).containsEntry("batchId", 42).containsEntry("title", "You are on a PIP");
        assertThat(dto.read()).isFalse();
    }

    @Test
    void feed_unreadOnly_usesTheUnreadQuery() {
        when(notificationRepository.findByUserIdAndChannelAndReadAtIsNull(eq(7L), eq(NotificationChannel.IN_APP), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.feed(7L, true, PageRequest.of(0, 20));

        verify(notificationRepository).findByUserIdAndChannelAndReadAtIsNull(eq(7L), eq(NotificationChannel.IN_APP), any(Pageable.class));
        verify(notificationRepository, never()).findByUserIdAndChannel(any(), any(), any());
    }

    @Test
    void feed_badPayload_surfacesEmptyMapNotAnError() {
        Notification row = inApp(2L, "not json at all", Instant.now());
        when(notificationRepository.findByUserIdAndChannel(eq(7L), eq(NotificationChannel.IN_APP), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        PageResponse<NotificationResponse> page = service.feed(7L, false, PageRequest.of(0, 20));

        assertThat(page.content().get(0).payload()).isEmpty();
        assertThat(page.content().get(0).read()).isTrue();
    }

    @Test
    void markRead_stampsReadAtOnce_andIsIdempotent() {
        Notification row = inApp(3L, "{}", null);
        when(notificationRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(row));

        NotificationResponse first = service.markRead(3L, 7L);
        assertThat(first.read()).isTrue();
        Instant stamped = row.getReadAt();
        assertThat(stamped).isNotNull();

        NotificationResponse second = service.markRead(3L, 7L);
        assertThat(row.getReadAt()).isEqualTo(stamped);
        assertThat(second.readAt()).isEqualTo(stamped);
    }

    @Test
    void markRead_foreignId_throwsNotFound() {
        when(notificationRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(99L, 7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAllRead_returnsRowCountFromBulkUpdate() {
        when(notificationRepository.markAllReadForUser(eq(7L), any(Instant.class))).thenReturn(4);

        assertThat(service.markAllRead(7L).markedRead()).isEqualTo(4);
    }
}
