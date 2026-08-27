package com.moriah.skillhub.submission;

import com.moriah.skillhub.submission.entity.PrState;
import com.moriah.skillhub.submission.entity.TaskSubmission;
import com.moriah.skillhub.submission.gateway.GithubVerificationService;
import com.moriah.skillhub.submission.gateway.PrVerification;
import com.moriah.skillhub.submission.repository.TaskSubmissionRepository;
import com.moriah.skillhub.common.job.JobRunTracker;
import com.moriah.skillhub.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** build-plan.md feature 12: "GitHub 5xx -> ... retry job verifies later." {@code
 * JobRunTracker} is unused by {@link SubmissionVerificationRetryJob#retry} directly — tested via
 * that method, same convention as {@code SubscriptionExpiryJobTest} calling {@code runExpiry()}
 * rather than waiting on the real cron trigger. */
@ExtendWith(MockitoExtension.class)
class SubmissionVerificationRetryJobTest {

    @Mock
    private TaskSubmissionRepository taskSubmissionRepository;
    @Mock
    private GithubVerificationService githubVerificationService;
    @Mock
    private JobRunTracker jobRunTracker;

    @InjectMocks
    private SubmissionVerificationRetryJob job;

    private User student;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(1L);
        student.setGithubUsername("adalovelace");
    }

    private TaskSubmission submission(Long id, String prUrl) {
        TaskSubmission submission = new TaskSubmission();
        submission.setId(id);
        submission.setUser(student);
        submission.setPrUrl(prUrl);
        return submission;
    }

    @Test
    void retry_nowVerifiable_updatesTheRowAndCountsIt() {
        TaskSubmission pending = submission(1L, "https://github.com/adalovelace/repo/pull/1");
        when(taskSubmissionRepository.findByVerifiedAtIsNull()).thenReturn(List.of(pending));
        when(githubVerificationService.verify(pending.getPrUrl(), "adalovelace"))
                .thenReturn(Optional.of(new PrVerification("adalovelace", "repo", 1, PrState.OPEN, 2, "sha1")));

        int count = job.retry();

        assertThat(count).isEqualTo(1);
        assertThat(pending.getVerifiedAt()).isNotNull();
        assertThat(pending.getRepoOwner()).isEqualTo("adalovelace");
        verify(taskSubmissionRepository).saveAll(List.of(pending));
    }

    @Test
    void retry_stillUnreachable_leavesTheRowAloneAndDoesNotCountIt() {
        TaskSubmission pending = submission(1L, "https://github.com/adalovelace/repo/pull/1");
        when(taskSubmissionRepository.findByVerifiedAtIsNull()).thenReturn(List.of(pending));
        when(githubVerificationService.verify(pending.getPrUrl(), "adalovelace")).thenReturn(Optional.empty());

        int count = job.retry();

        assertThat(count).isZero();
        assertThat(pending.getVerifiedAt()).isNull();
        verify(taskSubmissionRepository).saveAll(List.of());
    }

    @Test
    void retry_oneRowThrows_doesNotCrashTheWholeSweep() {
        TaskSubmission bad = submission(1L, "https://github.com/adalovelace/repo/pull/1");
        TaskSubmission good = submission(2L, "https://github.com/adalovelace/repo/pull/2");
        when(taskSubmissionRepository.findByVerifiedAtIsNull()).thenReturn(List.of(bad, good));
        when(githubVerificationService.verify(bad.getPrUrl(), "adalovelace"))
                .thenThrow(new RuntimeException("PR was deleted"));
        when(githubVerificationService.verify(good.getPrUrl(), "adalovelace"))
                .thenReturn(Optional.of(new PrVerification("adalovelace", "repo", 2, PrState.MERGED, 1, "sha2")));

        int count = job.retry();

        assertThat(count).isEqualTo(1);
        assertThat(bad.getVerifiedAt()).isNull();
        assertThat(good.getVerifiedAt()).isNotNull();
    }

    @Test
    void retry_noUnverifiedRows_doesNothing() {
        when(taskSubmissionRepository.findByVerifiedAtIsNull()).thenReturn(List.of());

        int count = job.retry();

        assertThat(count).isZero();
        verify(githubVerificationService, never()).verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
