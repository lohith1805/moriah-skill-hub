package com.moriah.skillhub.submission.gateway;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.submission.entity.PrState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubVerificationServiceTest {

    @Mock
    private GithubPrFetcher githubPrFetcher;

    private GithubVerificationService service;

    @BeforeEach
    void setUp() {
        service = new GithubVerificationService(githubPrFetcher);
    }

    @Test
    void verify_malformedUrl_throwsInvalidPrUrl() {
        assertThatThrownBy(() -> service.verify("https://gitlab.com/owner/repo/pull/1", "octocat"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PR_URL);
    }

    @Test
    void verify_authorMatches_returnsVerification() {
        when(githubPrFetcher.fetch("octocat", "hello-world", 42)).thenReturn(
                new GithubPrFetcher.PullRequestDto(
                        new GithubPrFetcher.PullRequestDto.GithubUser("octocat"),
                        "open", false, 3,
                        new GithubPrFetcher.PullRequestDto.Head("abc123")));

        Optional<PrVerification> result = service.verify("https://github.com/octocat/hello-world/pull/42", "OctoCat");

        assertThat(result).isPresent();
        PrVerification pr = result.get();
        assertThat(pr.owner()).isEqualTo("octocat");
        assertThat(pr.repo()).isEqualTo("hello-world");
        assertThat(pr.prNumber()).isEqualTo(42);
        assertThat(pr.state()).isEqualTo(PrState.OPEN);
        assertThat(pr.commitCount()).isEqualTo(3);
        assertThat(pr.latestCommitSha()).isEqualTo("abc123");
    }

    @Test
    void verify_merged_isMergedRegardlessOfState() {
        when(githubPrFetcher.fetch("octocat", "hello-world", 42)).thenReturn(
                new GithubPrFetcher.PullRequestDto(
                        new GithubPrFetcher.PullRequestDto.GithubUser("octocat"),
                        "closed", true, 5,
                        new GithubPrFetcher.PullRequestDto.Head("def456")));

        PrVerification pr = service.verify("https://github.com/octocat/hello-world/pull/42", "octocat").orElseThrow();

        assertThat(pr.state()).isEqualTo(PrState.MERGED);
    }

    @Test
    void verify_authorMismatch_throwsForbidden() {
        when(githubPrFetcher.fetch("octocat", "hello-world", 42)).thenReturn(
                new GithubPrFetcher.PullRequestDto(
                        new GithubPrFetcher.PullRequestDto.GithubUser("someone-else"),
                        "open", false, 1,
                        new GithubPrFetcher.PullRequestDto.Head("abc123")));

        assertThatThrownBy(() -> service.verify("https://github.com/octocat/hello-world/pull/42", "octocat"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PR_AUTHOR_MISMATCH);
    }

    @Test
    void verify_githubUnreachable_returnsEmptyRatherThanThrowing() {
        when(githubPrFetcher.fetch("octocat", "hello-world", 42))
                .thenThrow(new GithubUnavailableException(new RuntimeException("connection reset")));

        Optional<PrVerification> result = service.verify("https://github.com/octocat/hello-world/pull/42", "octocat");

        assertThat(result).isEmpty();
    }

    @Test
    void verify_prNotFound_throwsBusinessException() {
        when(githubPrFetcher.fetch("octocat", "hello-world", 42))
                .thenThrow(new BusinessException(ErrorCode.PR_NOT_FOUND));

        assertThatThrownBy(() -> service.verify("https://github.com/octocat/hello-world/pull/42", "octocat"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PR_NOT_FOUND);
    }
}
