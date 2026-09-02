package com.moriah.skillhub.interview;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.interview.dto.InterviewResponse;
import com.moriah.skillhub.interview.dto.ScheduleInterviewRequest;
import com.moriah.skillhub.interview.dto.UpdateInterviewRequest;
import com.moriah.skillhub.interview.entity.InterviewMode;
import com.moriah.skillhub.interview.entity.InterviewStatus;
import com.moriah.skillhub.interview.entity.InterviewType;
import com.moriah.skillhub.interview.entity.StudentInterview;
import com.moriah.skillhub.interview.repository.StudentInterviewRepository;
import com.moriah.skillhub.user.entity.User;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock
    private StudentInterviewRepository interviewRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private InterviewService service;

    private User user(long id, String uuid, String name) {
        User u = new User();
        u.setId(id);
        u.setUuid(uuid);
        u.setFullName(name);
        return u;
    }

    private StudentInterview interview(long id) {
        StudentInterview i = new StudentInterview();
        i.setId(id);
        i.setStudentId(20L);
        i.setScheduledBy(5L);
        i.setInterviewType(InterviewType.MOCK);
        i.setScheduledAt(Instant.parse("2026-09-15T10:00:00Z"));
        i.setStatus(InterviewStatus.SCHEDULED);
        return i;
    }

    @Test
    void schedule_resolvesStudentByUuidAndSavesScheduled() {
        when(userRepository.findByUuid("stu-uuid")).thenReturn(Optional.of(user(20L, "stu-uuid", "Stu One")));
        when(interviewRepository.save(any(StudentInterview.class))).thenAnswer(inv -> {
            StudentInterview i = inv.getArgument(0);
            i.setId(1L);
            return i;
        });
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(20L, "stu-uuid", "Stu One"), user(5L, "pm-uuid", "PM One")));

        InterviewResponse response = service.schedule(new ScheduleInterviewRequest(
                "stu-uuid", InterviewType.TECHNICAL, Instant.parse("2026-09-15T10:00:00Z"),
                60, InterviewMode.ONLINE, null, "Jane Interviewer", "https://meet/x"), 5L);

        ArgumentCaptor<StudentInterview> captor = ArgumentCaptor.forClass(StudentInterview.class);
        verify(interviewRepository).save(captor.capture());
        assertThat(captor.getValue().getStudentId()).isEqualTo(20L);
        assertThat(captor.getValue().getScheduledBy()).isEqualTo(5L);
        assertThat(captor.getValue().getStatus()).isEqualTo(InterviewStatus.SCHEDULED);
        assertThat(response.studentUuid()).isEqualTo("stu-uuid");
        assertThat(response.scheduledByUuid()).isEqualTo("pm-uuid");
    }

    @Test
    void schedule_unknownStudent_throwsNotFound() {
        when(userRepository.findByUuid("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.schedule(new ScheduleInterviewRequest(
                "ghost", InterviewType.MOCK, Instant.now(), null, null, null, null, null), 5L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(interviewRepository, never()).save(any());
    }

    @Test
    void listForStaff_withStudentUuid_resolvesItToAnId() {
        when(userRepository.findByUuid("stu-uuid")).thenReturn(Optional.of(user(20L, "stu-uuid", "Stu One")));
        when(interviewRepository.search(eq(InterviewStatus.SCHEDULED), eq(InterviewType.MOCK), eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(interview(1L)), PageRequest.of(0, 20), 1));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        PageResponse<InterviewResponse> page = service.listForStaff(
                InterviewStatus.SCHEDULED, InterviewType.MOCK, "stu-uuid", PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        verify(interviewRepository).search(eq(InterviewStatus.SCHEDULED), eq(InterviewType.MOCK), eq(20L), any(Pageable.class));
    }

    @Test
    void listForStudent_queriesByCallerId() {
        when(interviewRepository.findByStudentId(eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(interview(1L)), PageRequest.of(0, 20), 1));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        PageResponse<InterviewResponse> page = service.listForStudent(20L, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        verify(interviewRepository).findByStudentId(eq(20L), any(Pageable.class));
    }

    @Test
    void update_marksCompletedWithFeedbackAndRating() {
        StudentInterview i = interview(3L);
        when(interviewRepository.findById(3L)).thenReturn(Optional.of(i));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        service.update(3L, new UpdateInterviewRequest(
                InterviewType.TECHNICAL, Instant.parse("2026-09-15T10:00:00Z"), 45, InterviewMode.ONSITE,
                "Office 2F", "Jane", null, InterviewStatus.COMPLETED, "Strong on DS, weak on SQL.", 7), 5L);

        assertThat(i.getStatus()).isEqualTo(InterviewStatus.COMPLETED);
        assertThat(i.getFeedback()).isEqualTo("Strong on DS, weak on SQL.");
        assertThat(i.getRating()).isEqualTo(7);
        assertThat(i.getMode()).isEqualTo(InterviewMode.ONSITE);
    }

    @Test
    void update_unknownId_throwsNotFound() {
        when(interviewRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, new UpdateInterviewRequest(
                InterviewType.MOCK, Instant.now(), null, null, null, null, null, InterviewStatus.SCHEDULED, null, null), 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancel_movesToCancelledOnce() {
        StudentInterview i = interview(3L);
        when(interviewRepository.findById(3L)).thenReturn(Optional.of(i));

        service.cancel(3L, 5L);
        assertThat(i.getStatus()).isEqualTo(InterviewStatus.CANCELLED);
        verify(auditLogService).record(eq(5L), eq("INTERVIEW_CANCELLED"), any(), any(), any(), any());

        service.cancel(3L, 5L);
        verify(auditLogService).record(eq(5L), eq("INTERVIEW_CANCELLED"), any(), any(), any(), any());
    }
}
