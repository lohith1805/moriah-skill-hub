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
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BA Meetings coordination (gap B1.14). CRUD for {@code ba_meetings}, BUSINESS_ANALYST/ADMIN
 * (gated on {@link BaMeetingController}). Lives in {@code client/} with the rest of the BA
 * surface. {@code UserRepository} injected directly for creator-uuid resolution — shared
 * identity primitive.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BaMeetingService {

    private final BaMeetingRepository meetingRepository;
    private final ClientProjectRepository clientProjectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<BaMeetingResponse> list(BaMeetingStatus status, Long clientProjectId, Pageable pageable) {
        var page = meetingRepository.search(status, clientProjectId, pageable);
        Map<Long, String> creatorUuids = resolveCreatorUuids(page.getContent());
        return PageResponse.from(page.map(m -> toResponse(m, creatorUuids)));
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

        auditLogService.record(callerUserId, "BA_MEETING_CREATED", "BaMeeting", meeting.getId(), null, meeting.getTitle());
        log.info("[ba/meetings] {} scheduled meeting {}", callerUserId, meeting.getId());
        return toResponse(meeting, resolveCreatorUuids(List.of(meeting)));
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

        auditLogService.record(callerUserId, "BA_MEETING_UPDATED", "BaMeeting", meeting.getId(), null, meeting.getStatus());
        return toResponse(meeting, resolveCreatorUuids(List.of(meeting)));
    }

    /** {@code DELETE} — moves to {@code CANCELLED}, never row-deletes. Idempotent. */
    @Transactional
    public void cancel(Long id, Long callerUserId) {
        BaMeeting meeting = requireMeeting(id);
        if (meeting.getStatus() != BaMeetingStatus.CANCELLED) {
            meeting.setStatus(BaMeetingStatus.CANCELLED);
            auditLogService.record(callerUserId, "BA_MEETING_CANCELLED", "BaMeeting", meeting.getId(), null, meeting.getTitle());
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

    private BaMeetingResponse toResponse(BaMeeting m, Map<Long, String> creatorUuids) {
        return new BaMeetingResponse(
                m.getId(), m.getTitle(), m.getAgenda(),
                m.getClientProject() == null ? null : m.getClientProject().getId(),
                m.getScheduledAt(), m.getDurationMinutes(), m.getLocation(),
                m.getStatus(), m.getMinutes(), creatorUuids.get(m.getCreatedBy()),
                m.getCreatedAt(), m.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
