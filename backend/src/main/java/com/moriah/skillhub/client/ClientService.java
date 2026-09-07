package com.moriah.skillhub.client;

import com.moriah.skillhub.auth.AuthService;
import com.moriah.skillhub.auth.dto.ForgotPasswordRequest;
import com.moriah.skillhub.client.dto.ClientResponse;
import com.moriah.skillhub.client.dto.CreateClientRequest;
import com.moriah.skillhub.client.entity.Client;
import com.moriah.skillhub.client.entity.ClientStatus;
import com.moriah.skillhub.client.repository.ClientRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.user.entity.Role;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserRole;
import com.moriah.skillhub.user.entity.UserStatus;
import com.moriah.skillhub.user.repository.RoleRepository;
import com.moriah.skillhub.user.repository.UserRepository;
import com.moriah.skillhub.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * build-plan.md feature 21: "CLIENT users are provisioned by ADMIN — a client cannot
 * self-register." {@link #create} optionally provisions the linked portal {@code User} in the
 * same call, reusing {@code contactPerson}/{@code email} rather than collecting a separate set
 * of login fields (see {@code CreateClientRequest}'s own Javadoc). {@code Role}/{@code
 * RoleRepository}/{@code UserRoleRepository} are injected directly from {@code user.repository}
 * here, the same treatment {@code AuthService#register}/{@code OAuth2Service} already give the
 * {@code User}/{@code Role}/{@code UserRole} shared-kernel model — {@code user/} isn't gated
 * behind a service-interface boundary the way {@code sprint}/{@code batch}/{@code pip} are.
 * <p>
 * The provisioned {@code User} follows {@code OAuth2Service#createStudentFromOAuth2}'s exact
 * precedent for "an account exists with no password yet": {@code status = ACTIVE}, {@code
 * emailVerifiedAt} stamped immediately (an ADMIN-provisioned account has no email ownership to
 * prove the way self-registration does), {@code passwordHash} left {@code null}. There is
 * deliberately no bespoke token/email mechanism here — the new user's "set your password" link
 * is issued by calling {@link AuthService#forgotPassword} directly, the exact same {@code
 * PasswordResetToken} + {@code NotificationService} path {@code POST /auth/password/forgot}
 * already uses (AGENTS.md: "never log a token, password, OTP" — this class never generates,
 * returns, or logs one itself, only ever delegates to the machinery that already handles that
 * correctly).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthService authService;
    private final AuditLogService auditLogService;

    @Transactional
    public ClientResponse create(CreateClientRequest request, Long callerUserId) {
        Client client = new Client();
        client.setCompanyName(request.companyName());
        client.setContactPerson(request.contactPerson());
        client.setEmail(request.email());
        client.setPhone(request.phone());
        client.setIndustry(request.industry());
        client.setStatus(ClientStatus.ACTIVE);

        if (request.provisionPortalLogin()) {
            client.setUser(provisionPortalUser(request, callerUserId));
        }

        clientRepository.save(client);
        log.info("[client/create] created client '{}' (portal login: {})",
                client.getCompanyName(), request.provisionPortalLogin());

        return toResponse(client);
    }

    private User provisionPortalUser(CreateClientRequest request, Long callerUserId) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        User user = new User();
        user.setFullName(request.contactPerson());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        Role clientRole = roleRepository.findByCode(RoleCode.CLIENT)
                .orElseThrow(() -> new IllegalStateException(
                        "CLIENT role missing — V4 seed data not applied, check Migration Ledger"));
        userRoleRepository.save(new UserRole(user.getId(), clientRole.getId()));

        // Same PasswordResetToken + NotificationService path POST /auth/password/forgot already
        // uses — see this class's own Javadoc for why this is deliberately not a second, bespoke
        // mechanism. userRepository.save above already flushed (IDENTITY generation), so
        // AuthService#forgotPassword's own findByEmail lookup, running in this same transaction,
        // sees the row.
        authService.forgotPassword(new ForgotPasswordRequest(request.email()));

        auditLogService.record(callerUserId, "CLIENT_PORTAL_USER_PROVISIONED", "User", user.getId(),
                null, RoleCode.CLIENT.name());
        return user;
    }

    private ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getCompanyName(),
                client.getContactPerson(),
                client.getEmail(),
                client.getPhone(),
                client.getIndustry(),
                client.getUser() == null ? null : client.getUser().getUuid(),
                client.getStatus());
    }
}
