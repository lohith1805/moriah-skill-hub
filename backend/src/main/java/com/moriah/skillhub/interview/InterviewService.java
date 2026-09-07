package com.moriah.skillhub.interview;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.interview.dto.InterviewResponse;
import com.moriah.skillhub.interview.dto.ScheduleInterviewRequest;
import com.moriah.skillhub.interview.dto.UpdateInterviewRequest;
import com.moriah.skillhub.interview.entity.InterviewStatus;
import com.moriah.skillhub.interview.entity.InterviewType;
import com.moriah.skillhub.interview.entity.StudentInterview;
import com.moriah.skillhub.interview.repository.StudentInterviewRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Student Interviews (gap B1.8). A TRAINER_PM/ADMIN schedules and manages interviews (gated on
 * {@link InterviewController}); a STUDENT reads only their own via {@code /interviews/me}.
 * {@code studentId}/{@code scheduledBy} are bare ids on the entity — this service resolves them
 * to/from {@code uuid} at the boundary, batch-loading users per page (no N+1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final StudentInterviewRepository interviewRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public InterviewResponse schedule(ScheduleInterviewRequest request, Long callerUserId) {
        User student = userRepository.findByUuid(request.studentUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, request.studentUuid()));

        StudentInterview interview = new StudentInterview();
        interview.setStudentId(student.getId());
        interview.setScheduledBy(callerUserId);
        interview.setInterviewType(request.interviewType());
        interview.setScheduledAt(request.scheduledAt());
        interview.setDurationMinutes(request.durationMinutes());
        interview.setMode(request.mode());
        interview.setLocation(blankToNull(request.location()));
        interview.setInterviewerName(blankToNull(request.interviewerName()));
        interview.setMeetingLink(blankToNull(request.meetingLink()));
        interview.setStatus(InterviewStatus.SCHEDULED);
        interviewRepository.save(interview);

        auditLogService.record(callerUserId, "INTERVIEW_SCHEDULED", "StudentInterview", interview.getId(),
                null, student.getUuid());
        log.info("[interviews] {} scheduled {} interview for student {}",
                callerUserId, interview.getInterviewType(), student.getUuid());
        return toResponse(interview, usersById(List.of(interview)));
    }

    @Transactional(readOnly = true)
    public PageResponse<InterviewResponse> listForStaff(InterviewStatus status, InterviewType type,
            String studentUuid, Pageable pageable) {
        Long studentId = studentUuid == null ? null
                : userRepository.findByUuid(studentUuid)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, studentUuid))
                        .getId();
        Page<StudentInterview> page = interviewRepository.search(status, type, studentId, pageable);
        Map<Long, User> users = usersById(page.getContent());
        return PageResponse.from(page.map(i -> toResponse(i, users)));
    }

    @Transactional(readOnly = true)
    public PageResponse<InterviewResponse> listForStudent(Long callerUserId, Pageable pageable) {
        Page<StudentInterview> page = interviewRepository.findByStudentId(callerUserId, pageable);
        Map<Long, User> users = usersById(page.getContent());
        return PageResponse.from(page.map(i -> toResponse(i, users)));
    }

    @Transactional
    public InterviewResponse update(Long id, UpdateInterviewRequest request, Long callerUserId) {
        StudentInterview interview = requireInterview(id);

        interview.setInterviewType(request.interviewType());
        interview.setScheduledAt(request.scheduledAt());
        interview.setDurationMinutes(request.durationMinutes());
        interview.setMode(request.mode());
        interview.setLocation(blankToNull(request.location()));
        interview.setInterviewerName(blankToNull(request.interviewerName()));
        interview.setMeetingLink(blankToNull(request.meetingLink()));
        interview.setStatus(request.status());
        interview.setFeedback(blankToNull(request.feedback()));
        interview.setRating(request.rating());

        auditLogService.record(callerUserId, "INTERVIEW_UPDATED", "StudentInterview", interview.getId(),
                null, interview.getStatus());
        return toResponse(interview, usersById(List.of(interview)));
    }

    /** {@code DELETE} — moves to {@code CANCELLED}, never row-deletes. Idempotent. */
    @Transactional
    public void cancel(Long id, Long callerUserId) {
        StudentInterview interview = requireInterview(id);
        if (interview.getStatus() != InterviewStatus.CANCELLED) {
            interview.setStatus(InterviewStatus.CANCELLED);
            auditLogService.record(callerUserId, "INTERVIEW_CANCELLED", "StudentInterview", interview.getId(), null, null);
        }
    }

    private StudentInterview requireInterview(Long id) {
        return interviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INTERVIEW_NOT_FOUND, id));
    }

    private Map<Long, User> usersById(List<StudentInterview> interviews) {
        List<Long> ids = interviews.stream()
                .flatMap(i -> Stream.of(i.getStudentId(), i.getScheduledBy()))
                .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private InterviewResponse toResponse(StudentInterview i, Map<Long, User> users) {
        User student = users.get(i.getStudentId());
        User scheduledBy = users.get(i.getScheduledBy());
        return new InterviewResponse(
                i.getId(),
                student == null ? null : student.getUuid(),
                student == null ? null : student.getFullName(),
                scheduledBy == null ? null : scheduledBy.getUuid(),
                i.getInterviewType(),
                i.getScheduledAt(),
                i.getDurationMinutes(),
                i.getMode(),
                i.getLocation(),
                i.getInterviewerName(),
                i.getMeetingLink(),
                i.getStatus(),
                i.getFeedback(),
                i.getRating(),
                i.getCreatedAt(),
                i.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
