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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level, not an IT — `/architect feature 05` decision: a full browser-driven OAuth round
 * trip against real Google/GitHub infrastructure isn't testable in an automated {@code mvn
 * verify} run (no real provider credentials in CI). This exercises {@link OAuth2Service}'s
 * account-linking logic directly against a constructed {@link OAuth2User}, the meaningful,
 * buildable proof at this stage — the same reasoning {@code EntitlementGuardIT} used for testing
 * against {@link com.moriah.skillhub.common.security.EntitlementGuard} directly rather than
 * through a real endpoint that doesn't exist yet.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2ServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private AuthService authService;

    @InjectMocks
    private OAuth2Service oAuth2Service;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private Role studentRole;

    @BeforeEach
    void setUp() {
        studentRole = new Role();
        studentRole.setId(1L);
        studentRole.setCode(RoleCode.STUDENT);
    }

    @Test
    void newVerifiedGoogleEmail_createsActiveStudentWithNoPasswordAndDelegatesToLogin() {
        OAuth2User googleUser = oidcLikeUser(Map.of(
                "email", "new-student@example.com",
                "email_verified", true,
                "name", "Ada Lovelace"));

        when(userRepository.findByEmail("new-student@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByCode(RoleCode.STUDENT)).thenReturn(Optional.of(studentRole));
        LoginResponse expected = LoginResponse.completed(null);
        when(authService.completeOrChallengeLogin(any(User.class), any(), any())).thenReturn(expected);

        LoginResponse result = oAuth2Service.handleOAuth2Login("google", googleUser, "ua", "1.2.3.4");

        assertThat(result).isSameAs(expected);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("new-student@example.com");
        assertThat(saved.getFullName()).isEqualTo("Ada Lovelace");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getEmailVerifiedAt()).isNotNull();
        assertThat(saved.getPasswordHash()).isNull();
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    void existingEmail_reusesTheSameUserRow_neverCreatesADuplicate() {
        User existing = new User();
        existing.setId(42L);
        existing.setEmail("already-registered@example.com");
        existing.setStatus(UserStatus.ACTIVE);

        OAuth2User googleUser = oidcLikeUser(Map.of(
                "email", "already-registered@example.com",
                "email_verified", true,
                "name", "Ada Lovelace"));

        when(userRepository.findByEmail("already-registered@example.com")).thenReturn(Optional.of(existing));
        LoginResponse expected = LoginResponse.completed(null);
        when(authService.completeOrChallengeLogin(existing, "ua", "1.2.3.4")).thenReturn(expected);

        LoginResponse result = oAuth2Service.handleOAuth2Login("google", googleUser, "ua", "1.2.3.4");

        assertThat(result).isSameAs(expected);
        verify(userRepository, never()).save(any(User.class));
        verify(roleRepository, never()).findByCode(any());
    }

    @Test
    void githubLogin_capturesGithubUsername_onNewAccount() {
        OAuth2User githubUser = githubLikeUser("dev-student@example.com", "adalovelace", "Ada Lovelace");

        when(userRepository.findByEmail("dev-student@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByCode(RoleCode.STUDENT)).thenReturn(Optional.of(studentRole));
        when(authService.completeOrChallengeLogin(any(User.class), any(), any()))
                .thenReturn(LoginResponse.completed(null));

        oAuth2Service.handleOAuth2Login("github", githubUser, "ua", "1.2.3.4");

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getGithubUsername()).isEqualTo("adalovelace");
    }

    @Test
    void githubLogin_backfillsGithubUsername_onAPreExistingAccountMissingIt() {
        User existing = new User();
        existing.setId(7L);
        existing.setEmail("existing@example.com");
        existing.setStatus(UserStatus.ACTIVE);
        existing.setGithubUsername(null);

        OAuth2User githubUser = githubLikeUser("existing@example.com", "adalovelace", "Ada Lovelace");

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));
        when(authService.completeOrChallengeLogin(existing, "ua", "1.2.3.4"))
                .thenReturn(LoginResponse.completed(null));

        oAuth2Service.handleOAuth2Login("github", githubUser, "ua", "1.2.3.4");

        verify(userRepository).save(existing);
        assertThat(existing.getGithubUsername()).isEqualTo("adalovelace");
    }

    @Test
    void googleEmailNotVerified_throwsBusinessExceptionAndCreatesNoUser() {
        OAuth2User googleUser = oidcLikeUser(Map.of(
                "email", "unverified@example.com",
                "email_verified", false,
                "name", "Someone"));

        assertThatThrownBy(() -> oAuth2Service.handleOAuth2Login("google", googleUser, "ua", "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.OAUTH_PROFILE_INCOMPLETE));
        verify(userRepository, never()).save(any());
    }

    @Test
    void missingEmailAttribute_throwsBusinessException() {
        OAuth2User userWithNoEmail = oidcLikeUser(Map.of("name", "Someone"));

        assertThatThrownBy(() -> oAuth2Service.handleOAuth2Login("google", userWithNoEmail, "ua", "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.OAUTH_PROFILE_INCOMPLETE));
    }

    @Test
    void suspendedExistingAccount_throwsForbidden_neverReachesLogin() {
        User suspended = new User();
        suspended.setId(9L);
        suspended.setEmail("suspended@example.com");
        suspended.setStatus(UserStatus.SUSPENDED);

        OAuth2User googleUser = oidcLikeUser(Map.of(
                "email", "suspended@example.com",
                "email_verified", true,
                "name", "Someone"));

        when(userRepository.findByEmail("suspended@example.com")).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> oAuth2Service.handleOAuth2Login("google", googleUser, "ua", "1.2.3.4"))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(authService, never()).completeOrChallengeLogin(any(), any(), any());
    }

    /** Google's registration is OIDC in production, but {@code OidcUser} isn't trivial to
     * construct standalone — a plain {@link DefaultOAuth2User} carrying the same attribute keys
     * ({@code email}, {@code email_verified}, {@code name}) exercises {@link OAuth2Service}'s
     * attribute-reading logic identically, since it only ever calls {@code getAttribute(...)}. */
    private OAuth2User oidcLikeUser(Map<String, Object> attributes) {
        Map<String, Object> withNameAttribute = new HashMap<>(attributes);
        withNameAttribute.putIfAbsent("sub", "google-subject-id");
        return new DefaultOAuth2User(List.of(), withNameAttribute, "sub");
    }

    private OAuth2User githubLikeUser(String email, String login, String name) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "12345"); // OAuth2Service never reads "id" — only its type as the
        // DefaultOAuth2User name-attribute key matters here, kept a String to sidestep any
        // ambiguity in how that class stringifies a non-String name attribute.
        attributes.put("login", login);
        attributes.put("name", name);
        attributes.put("email", email); // GithubOAuth2UserService already resolved this by the
        // time OAuth2Service sees it — see that class's Javadoc.
        return new DefaultOAuth2User(List.of(), attributes, "id");
    }
}
