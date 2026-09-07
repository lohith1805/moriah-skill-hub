package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.ClientRequestResponse;
import com.moriah.skillhub.client.entity.Client;
import com.moriah.skillhub.client.entity.ClientStatus;
import com.moriah.skillhub.client.repository.ClientRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserStatus;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The ADMIN-facing side of client self-registration ({@code /api/v1/admin/client-requests}) —
 * review queue, approve, reject. Approving flips the applicant {@code PENDING_APPROVAL -> ACTIVE}
 * (and their {@code clients} row {@code INACTIVE -> ACTIVE}); rejecting sets {@code REJECTED}.
 * Both email the applicant. See {@code ClientRegistrationService} for the registration side and
 * why this re-opens build-plan.md feature 21.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientApprovalService {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<ClientRequestResponse> list(UserStatus status, Pageable pageable) {
        UserStatus filter = status == null ? UserStatus.PENDING_APPROVAL : status;
        if (filter != UserStatus.PENDING_APPROVAL && filter != UserStatus.REJECTED) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        Page<User> page = userRepository.search(filter, RoleCode.CLIENT, pageable);
        List<Long> userIds = page.getContent().stream().map(User::getId).toList();
        Map<Long, Client> clientByUserId = userIds.isEmpty()
                ? Map.of()
                : clientRepository.findByUserIdIn(userIds).stream()
                        .collect(Collectors.toMap(c -> c.getUser().getId(), c -> c));

        return PageResponse.from(page.map(u -> toResponse(u, clientByUserId.get(u.getId()))));
    }

    @Transactional
    public ClientRequestResponse approve(String userUuid, Long callerUserId) {
        User user = requirePendingClient(userUuid);
        Client client = clientRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CLIENT_REQUEST_NOT_PENDING));

        user.setStatus(UserStatus.ACTIVE);
        // Approved by staff — there is no separate email-ownership proof step for this path.
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        client.setStatus(ClientStatus.ACTIVE);
        clientRepository.save(client);

        notificationService.enqueueAfterCommit(user.getId(), NotificationChannel.EMAIL,
                "CLIENT_REGISTRATION_APPROVED", Map.of(
                        "to", user.getEmail(),
                        "subject", "Your Moriah Skill Hub account is approved",
                        "body", "Welcome aboard. You can now sign in and submit your project requirements."));

        auditLogService.record(callerUserId, "CLIENT_REGISTRATION_APPROVED", "User", user.getId(),
                UserStatus.PENDING_APPROVAL, UserStatus.ACTIVE);

        log.info("[client/approve] '{}' approved", client.getCompanyName());
        return toResponse(user, client);
    }

    @Transactional
    public ClientRequestResponse reject(String userUuid, String reason, Long callerUserId) {
        User user = requirePendingClient(userUuid);
        Client client = clientRepository.findByUserId(user.getId()).orElse(null);

        user.setStatus(UserStatus.REJECTED);
        userRepository.save(user);

        notificationService.enqueueAfterCommit(user.getId(), NotificationChannel.EMAIL,
                "CLIENT_REGISTRATION_REJECTED", Map.of(
                        "to", user.getEmail(),
                        "subject", "Update on your Moriah Skill Hub registration",
                        "body", "After review, we're unable to approve your registration at this time. Reason: " + reason));

        auditLogService.record(callerUserId, "CLIENT_REGISTRATION_REJECTED", "User", user.getId(),
                UserStatus.PENDING_APPROVAL, Map.of("reason", reason));

        return toResponse(user, client);
    }

    private User requirePendingClient(String userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userUuid));
        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.CLIENT_REQUEST_NOT_PENDING);
        }
        return user;
    }

    private ClientRequestResponse toResponse(User user, Client client) {
        return new ClientRequestResponse(
                user.getUuid(), user.getFullName(), user.getEmail(), user.getPhone(),
                client == null ? null : client.getCompanyName(),
                client == null ? null : client.getIndustry(),
                user.getStatus(), user.getCreatedAt());
    }
}
