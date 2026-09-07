package com.moriah.skillhub.submission;

import com.moriah.skillhub.sprint.TaskService;
import com.moriah.skillhub.submission.dto.CreateSubmissionRequest;
import com.moriah.skillhub.submission.dto.SubmissionResponse;
import com.moriah.skillhub.submission.entity.PrState;
import com.moriah.skillhub.submission.entity.TaskSubmission;
import com.moriah.skillhub.submission.gateway.GithubVerificationService;
import com.moriah.skillhub.submission.gateway.PrVerification;
import com.moriah.skillhub.submission.repository.TaskSubmissionRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `/architect feature 12` decision, proven directly: {@code TaskService.markInReview} runs
 * before any GitHub call or insert, attempt numbers are server-computed, and the unique-
 * constraint race (library-docs.md "Webhook Idempotency"'s same reasoning) resolves to the row
 * that actually won rather than failing the caller.
 */
@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private TaskSubmissionRepository taskSubmissionRepository;
    @Mock
    private TaskSubmissionWriter taskSubmissionWriter;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TaskService taskService;
    @Mock
    private GithubVerificationService githubVerificationService;

    @InjectMocks
    private SubmissionService submissionService;

    @Captor
    private ArgumentCaptor<TaskSubmission> submissionCaptor;

    private User student;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(1L);
        student.setUuid("uuid-1");
        student.setFullName("Ada Lovelace");
        student.setGithubUsername("adalovelace");
    }

    @Test
    void create_firstAttempt_verifiedImmediately_movesTaskToInReviewAndPersists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(taskSubmissionRepository.findMaxAttemptNumber(10L, 1L)).thenReturn(null);
        when(githubVerificationService.verify("https://github.com/adalovelace/repo/pull/1", "adalovelace"))
                .thenReturn(Optional.of(new PrVerification("adalovelace", "repo", 1, PrState.OPEN, 3, "abc123")));
        when(taskSubmissionWriter.tryInsert(submissionCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateSubmissionRequest request = new CreateSubmissionRequest(
                10L, "https://github.com/adalovelace/repo/pull/1", null, "first try");

        SubmissionResponse response = submissionService.create(1L, request);

        verify(taskService).markInReview(10L, 1L);
        TaskSubmission saved = submissionCaptor.getValue();
        assertThat(saved.getAttemptNumber()).isEqualTo(1);
        assertThat(saved.getRepoOwner()).isEqualTo("adalovelace");
        assertThat(saved.getVerifiedAt()).isNotNull();
        assertThat(response.attemptNumber()).isEqualTo(1);
        assertThat(response.prState()).isEqualTo(PrState.OPEN);
    }

    @Test
    void create_resubmission_computesNextAttemptNumberFromExistingMax() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(taskSubmissionRepository.findMaxAttemptNumber(10L, 1L)).thenReturn(2);
        when(githubVerificationService.verify(anyString(), anyString())).thenReturn(Optional.empty());
        when(taskSubmissionWriter.tryInsert(submissionCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateSubmissionRequest request = new CreateSubmissionRequest(
                10L, "https://github.com/adalovelace/repo/pull/2", null, null);

        submissionService.create(1L, request);

        assertThat(submissionCaptor.getValue().getAttemptNumber()).isEqualTo(3);
    }

    @Test
    void create_githubUnreachable_persistsWithNullVerifiedAt() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(taskSubmissionRepository.findMaxAttemptNumber(10L, 1L)).thenReturn(null);
        when(githubVerificationService.verify(anyString(), anyString())).thenReturn(Optional.empty());
        when(taskSubmissionWriter.tryInsert(submissionCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateSubmissionRequest request = new CreateSubmissionRequest(
                10L, "https://github.com/adalovelace/repo/pull/1", null, null);

        SubmissionResponse response = submissionService.create(1L, request);

        assertThat(submissionCaptor.getValue().getVerifiedAt()).isNull();
        assertThat(response.verifiedAt()).isNull();
        assertThat(response.status().name()).isEqualTo("SUBMITTED");
    }

    @Test
    void create_doublePostRace_returnsTheRowThatActuallyWonInsteadOfFailing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(taskSubmissionRepository.findMaxAttemptNumber(10L, 1L)).thenReturn(null);
        when(githubVerificationService.verify(anyString(), anyString())).thenReturn(Optional.empty());
        when(taskSubmissionWriter.tryInsert(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        TaskSubmission existing = new TaskSubmission();
        existing.setId(99L);
        existing.setTaskId(10L);
        existing.setUser(student);
        existing.setAttemptNumber(1);
        when(taskSubmissionWriter.findExisting(10L, 1L, 1))
                .thenReturn(Optional.of(existing));

        CreateSubmissionRequest request = new CreateSubmissionRequest(
                10L, "https://github.com/adalovelace/repo/pull/1", null, null);

        SubmissionResponse response = submissionService.create(1L, request);

        assertThat(response.id()).isEqualTo(99L);
        // markInReview still only called once — the race is entirely at the save step
        verify(taskService, times(1)).markInReview(10L, 1L);
    }

    @Test
    void create_markInReviewRejectsCaller_neverPersistsAnything() {
        // verify() runs before markInReview (SubmissionService.create's own Javadoc: never hold
        // an outbound HTTP call inside a transaction, so verification happens first, before any
        // DB mutation) — so unlike before this reorder, GitHub *is* consulted even for a
        // bad/unauthorized taskId. What still must never happen is a database write.
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(githubVerificationService.verify(anyString(), anyString())).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new com.moriah.skillhub.common.exception.ForbiddenOperationException(
                        com.moriah.skillhub.common.exception.ErrorCode.NOT_RESOURCE_OWNER))
                .when(taskService).markInReview(10L, 1L);

        CreateSubmissionRequest request = new CreateSubmissionRequest(
                10L, "https://github.com/adalovelace/repo/pull/1", null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> submissionService.create(1L, request))
                .isInstanceOf(com.moriah.skillhub.common.exception.ForbiddenOperationException.class);

        verify(taskSubmissionWriter, never()).tryInsert(org.mockito.ArgumentMatchers.any());
    }
}
