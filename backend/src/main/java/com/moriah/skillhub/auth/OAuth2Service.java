package com.moriah.skillhub.auth;

import com.moriah.skillhub.auth.dto.LoginResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.user.entity.Role;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserRole;
import com.moriah.skillhub.user.entity.UserStatus;
import com.moriah.skillhub.user.repository.RoleRepository;
import com.moriah.skillhub.user.repository.UserRepository;
import com.moriah.skillhub.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * build-plan.md feature 05. Account linking is "reuse the existing {@code users} row by verified
 * email match," nothing more — no provider-linkage table exists (`/architect feature 05`
 * decision; architecture.md's ER model has no such table and no migration slot is reserved for
 * one). Called from {@code OAuth2AuthenticationSuccessHandler} with whatever {@link OAuth2User}
 * Spring Security resolved (an {@code OidcUser} for Google, a plain {@code OAuth2User} for
 * GitHub via {@link com.moriah.skillhub.auth.oauth2.GithubOAuth2UserService} — both expose
 * {@code "email"} uniformly by the time this class sees them).
 */
@Service
@RequiredArgsConstructor
public class OAuth2Service {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthService authService;

    @Transactional
    public LoginResponse handleOAuth2Login(String registrationId, OAuth2User oAuth2User,
                                            String userAgent, String ipAddress) {
        String email = requireVerifiedEmail(registrationId, oAuth2User);
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            user = createStudentFromOAuth2(registrationId, oAuth2User, email);
        } else if ("github".equals(registrationId)) {
            captureGithubUsernameIfMissing(user, oAuth2User);
        }

        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.TERMINATED) {
            throw new ForbiddenOperationException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        return authService.completeOrChallengeLogin(user, userAgent, ipAddress);
    }

    private User createStudentFromOAuth2(String registrationId, OAuth2User oAuth2User, String email) {
        User user = new User();
        user.setFullName(resolveFullName(registrationId, oAuth2User));
        user.setEmail(email);
        // build-plan.md feature 05: "First OAuth login creates a STUDENT with email_verified_at
        // set and no password hash" — passwordHash is left null (the field defaults to null).
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        if ("github".equals(registrationId)) {
            user.setGithubUsername((String) oAuth2User.getAttribute("login"));
        }
        userRepository.save(user);

        Role studentRole = roleRepository.findByCode(RoleCode.STUDENT)
                .orElseThrow(() -> new IllegalStateException(
                        "STUDENT role missing — V4 seed data not applied, check Migration Ledger"));
        userRoleRepository.save(new UserRole(user.getId(), studentRole.getId()));

        return user;
    }

    /** build-plan.md feature 05: "GitHub login captures github_username — feature 12 depends on
     * it" — applies even to a pre-existing account (e.g. one that registered by password first
     * and is now linking GitHub), not only on first creation, since this is the one moment a
     * verified GitHub username is reliably available. */
    private void captureGithubUsernameIfMissing(User user, OAuth2User oAuth2User) {
        String githubUsername = (String) oAuth2User.getAttribute("login");
        if (githubUsername != null && !githubUsername.equals(user.getGithubUsername())) {
            user.setGithubUsername(githubUsername);
            userRepository.save(user);
        }
    }

    private String resolveFullName(String registrationId, OAuth2User oAuth2User) {
        String name = (String) oAuth2User.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        // GitHub's "name" profile field is optional and often unset — "login" (the username)
        // always exists and is a reasonable fallback display name.
        if ("github".equals(registrationId)) {
            String login = (String) oAuth2User.getAttribute("login");
            if (login != null) {
                return login;
            }
        }
        throw new BusinessException(ErrorCode.OAUTH_PROFILE_INCOMPLETE);
    }

    private String requireVerifiedEmail(String registrationId, OAuth2User oAuth2User) {
        String email = (String) oAuth2User.getAttribute("email");
        if (email == null) {
            throw new BusinessException(ErrorCode.OAUTH_PROFILE_INCOMPLETE);
        }
        if ("google".equals(registrationId)
                && !Boolean.TRUE.equals(oAuth2User.getAttribute("email_verified"))) {
            throw new BusinessException(ErrorCode.OAUTH_PROFILE_INCOMPLETE);
        }
        // GitHub: GithubOAuth2UserService already filtered to a verified, primary email before
        // this class ever sees the principal — nothing further to check here.
        return email;
    }
}
