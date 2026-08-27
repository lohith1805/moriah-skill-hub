package com.moriah.skillhub.auth;

import com.moriah.skillhub.auth.dto.TwoFactorEnableResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.security.ChallengeTokenService;
import com.moriah.skillhub.common.security.TotpSecretCipher;
import com.moriah.skillhub.common.security.TotpService;
import com.moriah.skillhub.common.util.Base32Codec;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * build-plan.md feature 05: TOTP enable/verify/disable. Mandatory-for-ADMIN/HR_MANAGER
 * enforcement itself lives in {@link AuthService} and {@link OAuth2Service} (at login time,
 * per `/architect feature 05`) — this class only owns the setup/teardown lifecycle and the
 * login-challenge verification they both call into.
 */
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private static final String ISSUER = "Moriah Skill Hub";

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final TotpSecretCipher totpSecretCipher;
    private final ChallengeTokenService challengeTokenService;

    /**
     * {@code challengeToken} is non-null exactly for the mandatory-2FA path: an ADMIN/HR_MANAGER
     * who reached {@code LoginResponse.twoFactorSetupRequired = true} has no access token to
     * authenticate this call with (`/architect feature 05` follow-up — build-plan.md's "mandatory
     * for ADMIN and HR_MANAGER" wasn't actually enforced until this). The same challenge token
     * that gated their login stands in for one; {@link #verifyLoginChallenge} is what actually
     * confirms the secret this generates and flips {@code two_factor_enabled} on. For every other
     * caller — someone already logged in, voluntarily turning 2FA on — {@code challengeToken} is
     * null and {@code callerUserId} (from their real access token) identifies them instead.
     */
    @Transactional
    public TwoFactorEnableResponse enable(Long callerUserId, String challengeToken) {
        User user = resolveUserForSetup(callerUserId, challengeToken);
        if (user.isTwoFactorEnabled()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_ALREADY_ENABLED);
        }

        byte[] secret = totpService.generateSecret();
        user.setTwoFactorSecret(totpSecretCipher.encrypt(secret));
        userRepository.save(user);

        return new TwoFactorEnableResponse(
                Base32Codec.encode(secret),
                totpService.buildProvisioningUri(secret, user.getEmail(), ISSUER));
    }

    private User resolveUserForSetup(Long callerUserId, String challengeToken) {
        if (challengeToken != null) {
            Long userId = challengeTokenService.peekUserId(challengeToken)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_2FA_CHALLENGE));
            return userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_2FA_CHALLENGE));
        }
        return requireAuthenticatedUser(callerUserId);
    }

    /** Called by {@link AuthService} once credentials (password or OAuth) check out for a
     * 2FA-enabled user — issues the challenge token {@code LoginResponse} carries back to the
     * client. Kept behind this façade rather than having {@code AuthService} depend on {@link
     * ChallengeTokenService} directly, so the 2FA subsystem's storage choice (Redis, not a SQL
     * table — see that class's Javadoc) stays an implementation detail of this class. */
    public String issueLoginChallenge(User user) {
        return challengeTokenService.issue(user.getId());
    }

    /** Confirms setup — the first correct code after {@link #enable} flips
     * {@code two_factor_enabled} to true. Not the login-time challenge path; see
     * {@link #verifyLoginChallenge}. */
    @Transactional
    public void confirmSetup(Long callerUserId, String totpCode) {
        User user = requireAuthenticatedUser(callerUserId);
        if (user.isTwoFactorEnabled()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_ALREADY_ENABLED);
        }
        if (!verifyCodeAgainstStoredSecret(user, totpCode)) {
            throw new BusinessException(ErrorCode.INVALID_2FA_CODE);
        }

        user.setTwoFactorEnabled(true);
        userRepository.save(user);
    }

    /** Completes a 2FA-gated login. {@code challengeToken} is not deleted on a wrong code —
     * only {@link ChallengeTokenService#consume} on success — so the caller can retry within the
     * challenge's TTL instead of restarting login from credentials.
     * <p>
     * If {@code two_factor_enabled} isn't set yet, this is the mandatory-2FA first-time-setup
     * path (secret already created by {@link #enable}, called with this same challenge token) —
     * the first successful code confirms it, the same way {@link #confirmSetup} does for a
     * voluntary setup. */
    @Transactional
    public User verifyLoginChallenge(String challengeToken, String totpCode) {
        Long userId = challengeTokenService.peekUserId(challengeToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_2FA_CHALLENGE));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_2FA_CHALLENGE));

        if (!verifyCodeAgainstStoredSecret(user, totpCode)) {
            throw new BusinessException(ErrorCode.INVALID_2FA_CODE);
        }

        if (!user.isTwoFactorEnabled()) {
            user.setTwoFactorEnabled(true);
            userRepository.save(user);
        }

        challengeTokenService.consume(challengeToken);
        return user;
    }

    @Transactional
    public void disable(Long callerUserId, String totpCode) {
        User user = requireAuthenticatedUser(callerUserId);
        if (!user.isTwoFactorEnabled()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_NOT_ENABLED);
        }
        if (!verifyCodeAgainstStoredSecret(user, totpCode)) {
            throw new BusinessException(ErrorCode.INVALID_2FA_CODE);
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);
    }

    /** Guards against a caller reaching {@code /2fa/verify} or {@code /2fa/disable} without ever
     * having called {@code /2fa/enable} first — {@code two_factor_secret} is null in that case,
     * which would otherwise NPE inside {@code TotpSecretCipher.decrypt}. */
    private boolean verifyCodeAgainstStoredSecret(User user, String totpCode) {
        if (user.getTwoFactorSecret() == null) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_NOT_ENABLED);
        }
        byte[] secret = totpSecretCipher.decrypt(user.getTwoFactorSecret());
        return totpService.verifyCode(secret, totpCode);
    }

    /** {@code /2fa/enable}, {@code /2fa/verify} (setup-confirmation branch), and
     * {@code /2fa/disable} all sit under the entirely-public {@code /api/v1/auth/**} filter
     * path (SecurityConfig) — the same shape as logout/logout-all needing to manually check for
     * a caller identity the filter chain doesn't require. {@code callerUserId} is null when no
     * valid access token was presented. */
    private User requireAuthenticatedUser(Long callerUserId) {
        if (callerUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findById(callerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }
}
