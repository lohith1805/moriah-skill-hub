package com.moriah.skillhub.auth;

import com.moriah.skillhub.auth.dto.ForgotPasswordRequest;
import com.moriah.skillhub.auth.dto.LoginRequest;
import com.moriah.skillhub.auth.dto.LoginResponse;
import com.moriah.skillhub.auth.dto.LogoutRequest;
import com.moriah.skillhub.auth.dto.RefreshRequest;
import com.moriah.skillhub.auth.dto.RegisterRequest;
import com.moriah.skillhub.auth.dto.RegisterResponse;
import com.moriah.skillhub.auth.dto.ResetPasswordRequest;
import com.moriah.skillhub.auth.dto.TokenPairResponse;
import com.moriah.skillhub.auth.dto.TwoFactorVerifyRequest;
import com.moriah.skillhub.auth.dto.VerifyEmailRequest;
import com.moriah.skillhub.auth.entity.EmailVerificationToken;
import com.moriah.skillhub.auth.entity.PasswordResetToken;
import com.moriah.skillhub.auth.entity.RefreshToken;
import com.moriah.skillhub.auth.repository.EmailVerificationTokenRepository;
import com.moriah.skillhub.auth.repository.PasswordResetTokenRepository;
import com.moriah.skillhub.auth.repository.RefreshTokenRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.common.security.JwtProperties;
import com.moriah.skillhub.common.security.JwtService;
import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import com.moriah.skillhub.common.security.TokenRevocationService;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.user.entity.Role;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserRole;
import com.moriah.skillhub.user.entity.UserStatus;
import com.moriah.skillhub.user.repository.RoleRepository;
import com.moriah.skillhub.user.repository.UserRepository;
import com.moriah.skillhub.user.repository.UserRoleRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Feature 03's email/verification-link delivery stub is cleared here (feature 08,
 * progress-tracker.md Open Stubs table) — tokens were always generated, hashed, and stored
 * correctly; only dispatch was deferred. The raw token is never logged under any circumstance
 * (code-standards.md Security Rules: "Never log a token...") — it is embedded directly into the
 * link handed to {@code NotificationService}, never passed through a log statement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final TokenRevocationService tokenRevocationService;
    private final AuditLogService auditLogService;
    private final RefreshTokenRevocationService refreshTokenRevocationService;
    private final TwoFactorService twoFactorService;
    private final NotificationService notificationService;
    private final AuthLinkProperties authLinkProperties;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setGithubUsername(request.githubUsername());
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        userRepository.save(user);

        Role studentRole = roleRepository.findByCode(RoleCode.STUDENT)
                .orElseThrow(() -> new IllegalStateException(
                        "STUDENT role missing — V4 seed data not applied, check Migration Ledger"));
        userRoleRepository.save(new UserRole(user.getId(), studentRole.getId()));

        issueEmailVerificationToken(user);

        return new RegisterResponse(user.getUuid(), user.getFullName(), user.getEmail());
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String userAgent, String ipAddress) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditLogService.record(user != null ? user.getId() : null, "LOGIN_FAILED", "User",
                    user != null ? user.getId() : null, null, Map.of("email", request.email()));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new ForbiddenOperationException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.TERMINATED) {
            auditLogService.record(user.getId(), "LOGIN_FAILED", "User", user.getId(),
                    null, Map.of("reason", "account_status_" + user.getStatus()));
            throw new ForbiddenOperationException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        return completeOrChallengeLogin(user, userAgent, ipAddress);
    }

    /**
     * Shared by password login (above) and {@code OAuth2Service}'s success handler —
     * build-plan.md feature 05's ADMIN/HR_MANAGER-mandatory 2FA is enforced here, at login time,
     * once, rather than duplicated per entry point (`/architect feature 05` decision). Package-
     * private: {@code OAuth2Service} lives in this same package and calls it directly once it has
     * resolved (or created) the {@code User} for the OAuth identity. {@code lastLoginAt} is
     * deliberately only stamped on a login that actually completes — not on the credential check
     * alone — so it can't be misleading for an account whose 2FA step is never finished.
     * <p>
     * The role check is re-evaluated on every login, not just once at role-assignment time
     * (`/architect feature 05` decision) — an ADMIN/HR_MANAGER who disables 2FA doesn't get a
     * standing loophole, they just get challenged to set it up again on their very next login.
     * That's what makes this the actual enforcement point rather than {@code /2fa/disable} also
     * needing a role check of its own.
     */
    @Transactional
    LoginResponse completeOrChallengeLogin(User user, String userAgent, String ipAddress) {
        if (user.isTwoFactorEnabled()) {
            String challengeToken = twoFactorService.issueLoginChallenge(user);
            return LoginResponse.twoFactorChallenge(challengeToken, false);
        }
        if (requiresMandatoryTwoFactor(user)) {
            // No secret exists yet — the challenge token doubles as the caller's identity for
            // POST /2fa/enable too (there is no access token to authenticate that call with),
            // and TwoFactorService.verifyLoginChallenge flips two_factor_enabled on first success.
            String challengeToken = twoFactorService.issueLoginChallenge(user);
            return LoginResponse.twoFactorChallenge(challengeToken, true);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return LoginResponse.completed(issueTokenPair(user, userAgent, ipAddress).response());
    }

    /** build-plan.md feature 05: "mandatory for ADMIN and HR_MANAGER." */
    private boolean requiresMandatoryTwoFactor(User user) {
        List<RoleCode> roles = userRoleRepository.findRoleCodesByUserId(user.getId());
        return roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.HR_MANAGER);
    }

    /** Exchanges a 2FA challenge token + TOTP code for the real token pair — the counterpart to
     * {@link LoginResponse#twoFactorChallenge}. */
    @Transactional
    public TokenPairResponse completeTwoFactorLogin(TwoFactorVerifyRequest request, String userAgent, String ipAddress) {
        User user = twoFactorService.verifyLoginChallenge(request.challengeToken(), request.totpCode());

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return issueTokenPair(user, userAgent, ipAddress).response();
    }

    @Transactional
    public TokenPairResponse refresh(RefreshRequest request, String userAgent, String ipAddress) {
        String hash = OpaqueTokenGenerator.sha256Hex(request.refreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (existing.getReplacedBy() != null) {
            // A token that has already been rotated is being presented again — token theft.
            // Revoke the entire chain, not just this token (library-docs.md "JJWT"). This must go
            // through RefreshTokenRevocationService's own REQUIRES_NEW transaction, not a plain
            // repository call in this method's transaction — the throw immediately below rolls
            // this transaction back, and a same-transaction revocation would be rolled back right
            // along with it, silently undoing the chain revocation. See that class's Javadoc.
            Long userId = existing.getUser().getId();
            refreshTokenRevocationService.revokeAllForUser(userId);
            log.warn("[auth/refresh] reuse of already-rotated refresh token for user {} — chain revoked",
                    existing.getUser().getUuid());
            auditLogService.record(userId, "REFRESH_TOKEN_REUSE_DETECTED", "User", userId, null, null);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (existing.getRevokedAt() != null) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        IssuedTokenPair issued = issueTokenPair(existing.getUser(), userAgent, ipAddress);

        existing.setRevokedAt(Instant.now());
        existing.setReplacedBy(issued.refreshTokenEntityId());
        refreshTokenRepository.save(existing);

        return issued.response();
    }

    @Transactional
    public void logout(LogoutRequest request, String authorizationHeader) {
        String hash = OpaqueTokenGenerator.sha256Hex(request.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });

        denylistBearerTokenIfPresent(authorizationHeader);
    }

    @Transactional
    public void logoutAll(LogoutRequest request, String authorizationHeader) {
        String hash = OpaqueTokenGenerator.sha256Hex(request.refreshToken());
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        User user = token.getUser();
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), Instant.now());

        // Increments token_version — every access token issued before this instant fails
        // JwtAuthFilter's comparison on its very next use, satisfying "every previously issued
        // access token rejected within a second" (build-plan.md feature 03 verify line).
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        denylistBearerTokenIfPresent(authorizationHeader);
        auditLogService.record(user.getId(), "LOGOUT_ALL", "User", user.getId(), null, null);
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        String hash = OpaqueTokenGenerator.sha256Hex(request.token());
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OR_EXPIRED_VERIFICATION_TOKEN));

        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_OR_EXPIRED_VERIFICATION_TOKEN);
        }

        token.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerifiedAt(Instant.now());
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Same response regardless of whether the email exists — no account-enumeration signal.
        userRepository.findByEmail(request.email()).ifPresent(this::issuePasswordResetToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hash = OpaqueTokenGenerator.sha256Hex(request.token());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OR_EXPIRED_RESET_TOKEN));

        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_OR_EXPIRED_RESET_TOKEN);
        }

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // A password reset invalidates every existing session — the same mechanism as suspend
        // and logout-all (library-docs.md "JJWT").
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        refreshTokenRepository.revokeAllActiveForUser(user.getId(), Instant.now());
        auditLogService.record(user.getId(), "PASSWORD_RESET", "User", user.getId(), null, null);
    }

    private void issueEmailVerificationToken(User user) {
        String raw = OpaqueTokenGenerator.generate();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(OpaqueTokenGenerator.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(Constants.EMAIL_VERIFICATION_TOKEN_TTL_HOURS, ChronoUnit.HOURS));
        emailVerificationTokenRepository.save(token);

        String link = authLinkProperties.emailVerificationUrlTemplate().replace("{token}", raw);
        notificationService.enqueueAfterCommit(user.getId(), NotificationChannel.EMAIL, "EMAIL_VERIFICATION", Map.of(
                "to", user.getEmail(),
                "subject", "Verify your email — Moriah Skill Hub",
                "body", "Verify your email by visiting: " + link));
    }

    private void issuePasswordResetToken(User user) {
        String raw = OpaqueTokenGenerator.generate();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(OpaqueTokenGenerator.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(Constants.PASSWORD_RESET_TOKEN_TTL_HOURS, ChronoUnit.HOURS));
        passwordResetTokenRepository.save(token);

        String link = authLinkProperties.passwordResetUrlTemplate().replace("{token}", raw);
        notificationService.enqueueAfterCommit(user.getId(), NotificationChannel.EMAIL, "PASSWORD_RESET", Map.of(
                "to", user.getEmail(),
                "subject", "Reset your password — Moriah Skill Hub",
                "body", "Reset your password by visiting: " + link));
    }

    private IssuedTokenPair issueTokenPair(User user, String userAgent, String ipAddress) {
        List<String> roles = userRoleRepository.findRoleCodesByUserId(user.getId()).stream()
                .map(RoleCode::name)
                .toList();

        String accessToken = jwtService.generateAccessToken(user, roles);

        String rawRefreshToken = OpaqueTokenGenerator.generate();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(OpaqueTokenGenerator.sha256Hex(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenDays(), ChronoUnit.DAYS));
        refreshToken.setUserAgent(userAgent);
        refreshToken.setIpAddress(ipAddress);
        RefreshToken saved = refreshTokenRepository.save(refreshToken);

        TokenPairResponse response = new TokenPairResponse(
                accessToken, rawRefreshToken, jwtProperties.accessTokenMinutes() * 60);
        return new IssuedTokenPair(response, saved.getId());
    }

    private void denylistBearerTokenIfPresent(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        try {
            Jws<Claims> jws = jwtService.parse(authorizationHeader.substring(7));
            Claims claims = jws.getPayload();
            Duration remaining = Duration.between(Instant.now(), claims.getExpiration().toInstant());
            tokenRevocationService.denylist(claims.getId(), remaining);
        } catch (JwtException e) {
            // Already invalid or expired — nothing to denylist.
            log.debug("[auth/logout] bearer token not denylisted (already invalid): {}", e.getMessage());
        }
    }

    private record IssuedTokenPair(TokenPairResponse response, Long refreshTokenEntityId) {
    }
}
