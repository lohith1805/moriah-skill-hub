package com.moriah.skillhub.client;

import com.moriah.skillhub.auth.dto.ClientRegisterRequest;
import com.moriah.skillhub.auth.dto.RegisterResponse;
import com.moriah.skillhub.client.entity.Client;
import com.moriah.skillhub.client.entity.ClientStatus;
import com.moriah.skillhub.client.repository.ClientRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * {@code POST /api/v1/auth/register/client} — corporate-client self-registration.
 * <p>
 * This deliberately re-opens build-plan.md feature 21's "CLIENT users are provisioned by ADMIN —
 * a client cannot self-register" (frontend-integration decision, 2026-09-02). The account is
 * created in {@link UserStatus#PENDING_APPROVAL} with the password the applicant chose, and
 * {@code AuthService.login} refuses it ({@code ACCOUNT_PENDING_APPROVAL}) until an ADMIN approves
 * it via {@code ClientApprovalService}. {@code ClientService#create} (ADMIN-provisioned, active
 * immediately, set-password email) is untouched and still the path staff use.
 * <p>
 * The linked {@code clients} row is created now, as {@link ClientStatus#INACTIVE} — the pending
 * state lives on the user, and approval flips the client row to {@code ACTIVE}. No separate
 * client-status value is added ({@code ClientStatus} stays ACTIVE/INACTIVE).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientRegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional
    public RegisterResponse register(ClientRegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        if (request.phone() != null && !request.phone().isBlank()
                && userRepository.existsByPhone(request.phone())) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.PENDING_APPROVAL);
        userRepository.save(user);

        Role clientRole = roleRepository.findByCode(RoleCode.CLIENT)
                .orElseThrow(() -> new IllegalStateException(
                        "CLIENT role missing — V4 seed data not applied, check Migration Ledger"));
        userRoleRepository.save(new UserRole(user.getId(), clientRole.getId()));

        Client client = new Client();
        client.setCompanyName(request.companyName());
        client.setContactPerson(request.fullName());
        client.setEmail(request.email());
        client.setPhone(request.phone());
        client.setIndustry(request.industry());
        client.setStatus(ClientStatus.INACTIVE);
        client.setUser(user);
        clientRepository.save(client);

        notificationService.enqueueAfterCommit(user.getId(), NotificationChannel.EMAIL,
                "CLIENT_REGISTRATION_RECEIVED", Map.of(
                        "to", user.getEmail(),
                        "subject", "We've received your Moriah Skill Hub registration",
                        "body", "Thanks for registering " + request.companyName() + ". Our team will review "
                                + "your request and email you once it's approved."));

        auditLogService.record(user.getId(), "CLIENT_REGISTRATION_SUBMITTED", "User", user.getId(),
                null, Map.of("companyName", request.companyName()));

        log.info("[client/self-register] '{}' submitted, pending approval", request.companyName());
        return new RegisterResponse(user.getUuid(), user.getFullName(), user.getEmail());
    }
}
