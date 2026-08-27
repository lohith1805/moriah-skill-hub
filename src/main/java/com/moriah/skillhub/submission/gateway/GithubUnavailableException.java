package com.moriah.skillhub.submission.gateway;

/** Package-private, deliberately never crosses {@link GithubVerificationService}'s own boundary —
 * {@code verify()} catches this internally and returns {@code Optional.empty()} instead
 * (build-plan.md feature 12: "GitHub 5xx -> persist verified_at = null ... An outage never
 * blocks a student"). A malformed URL, a genuinely missing PR, or an author mismatch are real
 * client-input problems and throw a normal {@code BusinessException}/{@code
 * ForbiddenOperationException} instead — only "GitHub itself is unreachable right now" takes this
 * path. */
class GithubUnavailableException extends RuntimeException {

    GithubUnavailableException(Throwable cause) {
        super(cause);
    }
}
