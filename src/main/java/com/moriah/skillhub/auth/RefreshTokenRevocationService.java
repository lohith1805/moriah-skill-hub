package com.moriah.skillhub.auth;

import com.moriah.skillhub.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * A separate bean, not a private method on {@link AuthService}, for the same reason {@code
 * AuditLogService} is {@code REQUIRES_NEW} and lives in its own class (code-standards.md
 * "Transactions": "Use REQUIRES_NEW only for audit and webhook-event recording that must survive
 * a rollback, and comment why"). Chain revocation on a detected refresh-token reuse is the same
 * shape of problem: {@code AuthService.refresh()} is {@code @Transactional}, and it throws
 * {@code INVALID_REFRESH_TOKEN} immediately after detecting reuse — if the revocation ran in
 * that same transaction, the exception's rollback would silently undo the revocation too,
 * defeating "reuse of a consumed token revokes the whole chain" (build-plan.md feature 03) in
 * exactly the case that guarantee exists for: a token that has actually been stolen and reused.
 * <p>
 * A same-class {@code REQUIRES_NEW} method called via {@code this.} would bypass Spring's
 * transactional proxy entirely (the same AOP self-invocation pitfall documented on {@code
 * EntitlementFlagsLoader}), so this has to be its own bean, called cross-bean from {@link
 * AuthService}.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now());
    }
}
