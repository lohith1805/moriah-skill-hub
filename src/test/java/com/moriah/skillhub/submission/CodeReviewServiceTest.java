package com.moriah.skillhub.submission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.sprint.TaskService;
import com.moriah.skillhub.sprint.entity.TaskStatus;
import com.moriah.skillhub.submission.dto.CreateReviewRequest;
import com.moriah.skillhub.submission.dto.InlineComment;
import com.moriah.skillhub.submission.dto.ReviewResponse;
import com.moriah.skillhub.submission.entity.CodeReview;
import com.moriah.skillhub.submission.entity.ReviewVerdict;
import com.moriah.skillhub.submission.entity.SubmissionStatus;
import com.moriah.skillhub.submission.entity.TaskSubmission;
import com.moriah.skillhub.submission.repository.CodeReviewRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Proves the verdict-to-task-status mapping directly — {@code /architect feature 12}'s
 * decision that {@code CHANGES_REQUESTED} sends the task back to {@code IN_PROGRESS}, never the
 * terminal {@code REJECTED} (see {@code TaskService.ALLOWED_TRANSITIONS}' Javadoc). */
@ExtendWith(MockitoExtension.class)
class CodeReviewServiceTest {

    @Mock
    private CodeReviewRepository codeReviewRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubmissionService submissionService;
    @Mock
    private TaskService taskService;
    @Mock
    private AuditLogService auditLogService;

    private CodeReviewService codeReviewService;

    @Captor
    private ArgumentCaptor<CodeReview> reviewCaptor;

    private User reviewer;
    private TaskSubmission submission;

    @BeforeEach
    void setUp() {
        codeReviewService = new CodeReviewService(
                codeReviewRepository, userRepository, submissionService, taskService, new ObjectMapper(), auditLogService);

        reviewer = new User();
        reviewer.setId(2L);
        reviewer.setUuid("uuid-pm");
        reviewer.setFullName("PM Reviewer");

        User student = new User();
        student.setId(1L);
        student.setUuid("uuid-student");
        student.setFullName("Student One");

        submission = new TaskSubmission();
        submission.setId(50L);
        submission.setTaskId(10L);
        submission.setUser(student);
        submission.setAttemptNumber(1);
    }

    @Test
    void create_approved_completesTheTaskAndMarksSubmissionApproved() {
        when(submissionService.requireSubmission(50L)).thenReturn(submission);
        when(userRepository.findById(2L)).thenReturn(Optional.of(reviewer));
        when(codeReviewRepository.save(reviewCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        CreateReviewRequest request = new CreateReviewRequest(50L, 9, ReviewVerdict.APPROVED, "Great work", List.of());

        ReviewResponse response = codeReviewService.create(2L, request);

        verify(taskService).completeReview(10L, 2L, TaskStatus.COMPLETED);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.APPROVED);
        assertThat(response.verdict()).isEqualTo(ReviewVerdict.APPROVED);
        assertThat(response.score()).isEqualTo(9);
    }

    @Test
    void create_changesRequested_sendsTaskBackToInProgressNotRejected() {
        when(submissionService.requireSubmission(50L)).thenReturn(submission);
        when(userRepository.findById(2L)).thenReturn(Optional.of(reviewer));
        when(codeReviewRepository.save(reviewCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        CreateReviewRequest request = new CreateReviewRequest(
                50L, 4, ReviewVerdict.CHANGES_REQUESTED, "Fix the tests",
                List.of(new InlineComment("src/Main.java", 12, "Missing null check")));

        codeReviewService.create(2L, request);

        verify(taskService).completeReview(10L, 2L, TaskStatus.IN_PROGRESS);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.CHANGES_REQUESTED);
    }

    @Test
    void create_inlineComments_roundTripThroughJson() {
        when(submissionService.requireSubmission(50L)).thenReturn(submission);
        when(userRepository.findById(2L)).thenReturn(Optional.of(reviewer));
        when(codeReviewRepository.save(reviewCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        List<InlineComment> comments = List.of(
                new InlineComment("src/Main.java", 12, "Missing null check"),
                new InlineComment("src/Util.java", 4, "Unused import"));
        CreateReviewRequest request = new CreateReviewRequest(50L, 6, ReviewVerdict.CHANGES_REQUESTED, null, comments);

        ReviewResponse response = codeReviewService.create(2L, request);

        assertThat(reviewCaptor.getValue().getInlineComments()).contains("Missing null check");
        assertThat(response.inlineComments()).containsExactlyElementsOf(comments);
    }

    @Test
    void create_noInlineComments_storesNullNotEmptyArray() {
        when(submissionService.requireSubmission(50L)).thenReturn(submission);
        when(userRepository.findById(2L)).thenReturn(Optional.of(reviewer));
        when(codeReviewRepository.save(reviewCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        CreateReviewRequest request = new CreateReviewRequest(50L, 8, ReviewVerdict.APPROVED, "Nice", null);

        ReviewResponse response = codeReviewService.create(2L, request);

        assertThat(reviewCaptor.getValue().getInlineComments()).isNull();
        assertThat(response.inlineComments()).isEmpty();
    }
}
