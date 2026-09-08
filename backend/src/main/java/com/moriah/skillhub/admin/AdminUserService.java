package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.AdminUserDetailResponse;
import com.moriah.skillhub.admin.dto.AdminUserResponse;
import com.moriah.skillhub.admin.dto.CreateStaffRequest;
import com.moriah.skillhub.admin.dto.UpdateUserRequest;
import com.moriah.skillhub.admin.dto.UpdateUserRolesRequest;
import com.moriah.skillhub.admin.dto.UpdateUserStatusRequest;
import com.moriah.skillhub.auth.AuthService;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.user.dto.UserRoleCodeProjection;
import com.moriah.skillhub.user.entity.Role;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserRole;
import com.moriah.skillhub.user.entity.UserStatus;
import com.moriah.skillhub.user.repository.RoleRepository;
import com.moriah.skillhub.user.repository.UserRepository;
import com.moriah.skillhub.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code GET /admin/users}, {@code PUT /admin/users/{userUuid}/status}, {@code PUT
 * /admin/users/{userUuid}/roles} — all against the primary (small reads/writes on {@code
 * users}/{@code user_roles}, not the heavy-read replica traffic build-plan.md singles out for
 * exports/metrics). Injects {@code UserRepository}/{@code UserRoleRepository}/{@code
 * RoleRepository} directly rather than routing through {@code user/UserService} — the established
 * precedent across this codebase (e.g. {@code ClientService}, {@code CertificateService},
 * {@code LeadService} all do the same for {@code User}) is that {@code User}/{@code UserRole}/
 * {@code Role} are shared identity primitives every module reads/writes directly, not a package
 * whose repository is off-limits to siblings the way a domain aggregate like {@code SubscriptionPlan}
 * is (see {@code AdminPlanController}, which correctly routes through {@code EntitlementService}
 * instead).
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(RoleCode role, UserStatus status, Pageable pageable) {
        Page<User> page = userRepository.search(status, role, pageable);

        List<Long> userIds = page.getContent().stream().map(User::getId).toList();
        Map<Long, List<RoleCode>> rolesByUserId = userIds.isEmpty()
                ? Map.of()
                : userRoleRepository.findRoleCodesByUserIds(userIds).stream()
                        .collect(Collectors.groupingBy(UserRoleCodeProjection::userId,
                                Collectors.mapping(UserRoleCodeProjection::roleCode, Collectors.toList())));

        return PageResponse.from(page.map(user -> toResponse(user, rolesByUserId.getOrDefault(user.getId(), List.of()))));
    }

    /** build-plan.md feature 22: "Suspension and role change increment {@code token_version}, so
     * revocation is immediate rather than nominal." Same increment-and-save mechanism {@code
     * AuthService.logoutAll}/{@code resetPassword} already use for the same reason — no dedicated
     * helper existed to reuse, so this mirrors their exact one-line pattern rather than inventing
     * a new one. Bumped for any status change, not only {@code SUSPENDED} specifically — a
     * {@code TERMINATED} account's outstanding token should be just as dead, and there is no
     * status transition here for which "leave existing sessions valid" is the right behaviour. */
    @Transactional
    public AdminUserResponse updateStatus(String userUuid, UpdateUserStatusRequest request, Long callerUserId) {
        User user = requireUser(userUuid);
        UserStatus oldStatus = user.getStatus();

        user.setStatus(request.status());
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        auditLogService.record(callerUserId, "USER_STATUS_CHANGED", "User", user.getId(), oldStatus, request.status());

        return toResponse(user, currentRoles(user.getId()));
    }

    /** Same {@code token_version} bump as {@link #updateStatus}, same build-plan.md line covering
     * both. Roles are replaced wholesale (delete-then-insert), not diffed — {@code user_roles} has
     * no ordering/metadata worth preserving per row, so a full replace is simpler and no less
     * correct than computing an add/remove delta. */
    @Transactional
    public AdminUserResponse updateRoles(String userUuid, UpdateUserRolesRequest request, Long callerUserId) {
        User user = requireUser(userUuid);
        List<RoleCode> oldRoles = currentRoles(user.getId());

        List<Role> newRoles = request.roles().stream()
                .map(code -> roleRepository.findByCode(code)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, code)))
                .toList();

        userRoleRepository.deleteByIdUserId(user.getId());
        userRoleRepository.saveAll(newRoles.stream()
                .map(role -> new UserRole(user.getId(), role.getId()))
                .toList());

        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        auditLogService.record(callerUserId, "USER_ROLES_CHANGED", "User", user.getId(), oldRoles, request.roles());

        return toResponse(user, newRoles.stream().map(Role::getCode).toList());
    }

    /**
     * {@code POST /api/v1/admin/users} — creates a staff account in {@code INVITED} state and
     * emails an accept-invite link (there is no other runtime path to onboard a
     * TRAINER_PM/DEVELOPER/LEAD_GEN/HR_MANAGER/BUSINESS_ANALYST/ADMIN — {@code /auth/register}
     * only ever mints a STUDENT). No {@code token_version} bump: a brand-new account has no
     * outstanding tokens. Delegates the token + email to {@code AuthService#issueStaffInvite},
     * the same way {@code ClientService} delegates its set-password email — {@code AuthService}
     * owns every token/link this system emails.
     */
    @Transactional
    public AdminUserResponse inviteStaff(CreateStaffRequest request, Long callerUserId) {
        for (RoleCode code : request.roles()) {
            if (code == RoleCode.STUDENT || code == RoleCode.CLIENT) {
                throw new BusinessException(ErrorCode.ROLE_NOT_STAFF_ASSIGNABLE);
            }
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        List<Role> roles = request.roles().stream()
                .map(code -> roleRepository.findByCode(code)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, code)))
                .toList();

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        // blank -> null: users.phone carries a UNIQUE index, and "" is a value, so a second
        // phone-less invite would collide on the empty string. The update path already does this.
        user.setPhone(blankToNull(request.phone()));
        user.setStatus(UserStatus.INVITED);
        userRepository.save(user);

        userRoleRepository.saveAll(roles.stream()
                .map(role -> new UserRole(user.getId(), role.getId()))
                .toList());

        authService.issueStaffInvite(user);

        List<RoleCode> roleCodes = roles.stream().map(Role::getCode).toList();
        auditLogService.record(callerUserId, "STAFF_INVITED", "User", user.getId(), null, roleCodes);
        return toResponse(user, roleCodes);
    }

    /** {@code POST /api/v1/admin/users/{userUuid}/resend-invite} — re-issues the accept-invite
     * link (burning the previous one) for an account still in {@code INVITED} state. */
    @Transactional
    public AdminUserResponse resendStaffInvite(String userUuid, Long callerUserId) {
        User user = requireUser(userUuid);
        if (user.getStatus() != UserStatus.INVITED) {
            throw new BusinessException(ErrorCode.INVITE_NOT_PENDING);
        }
        authService.issueStaffInvite(user);
        auditLogService.record(callerUserId, "STAFF_INVITE_RESENT", "User", user.getId(), null, null);
        return toResponse(user, currentRoles(user.getId()));
    }

    /** {@code GET /api/v1/admin/users/{userUuid}} (gap B1.17) — single-user detail. */
    @Transactional(readOnly = true)
    public AdminUserDetailResponse get(String userUuid) {
        User user = requireUser(userUuid);
        return toDetailResponse(user, currentRoles(user.getId()));
    }

    /**
     * {@code PUT /api/v1/admin/users/{userUuid}} (gap B1.17) — edits profile fields only.
     * Status and roles have their own endpoints (and their own {@code token_version} bump); a
     * name/phone/links change touches nothing security-relevant, so {@code token_version} is
     * left alone here. A {@code phone} collision surfaces as a 409 through the {@code users.phone}
     * unique constraint + {@code GlobalExceptionHandler}, the same way {@code AuthService.register}
     * already relies on it.
     */
    @Transactional
    public AdminUserDetailResponse update(String userUuid, UpdateUserRequest request, Long callerUserId) {
        User user = requireUser(userUuid);

        user.setFullName(request.fullName());
        user.setPhone(blankToNull(request.phone()));
        user.setGithubUsername(blankToNull(request.githubUsername()));
        user.setLinkedinUrl(blankToNull(request.linkedinUrl()));
        userRepository.save(user);

        auditLogService.record(callerUserId, "USER_PROFILE_UPDATED", "User", user.getId(), null, user.getUuid());
        return toDetailResponse(user, currentRoles(user.getId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<RoleCode> currentRoles(Long userId) {
        return userRoleRepository.findRoleCodesByUserId(userId);
    }

    private User requireUser(String userUuid) {
        return userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userUuid));
    }

    private AdminUserResponse toResponse(User user, List<RoleCode> roles) {
        return new AdminUserResponse(
                user.getUuid(), user.getFullName(), user.getEmail(), user.getStatus(), sortRoles(roles), user.getCreatedAt());
    }

    private AdminUserDetailResponse toDetailResponse(User user, List<RoleCode> roles) {
        return new AdminUserDetailResponse(
                user.getUuid(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getGithubUsername(), user.getLinkedinUrl(), user.getStatus(), sortRoles(roles),
                user.isTwoFactorEnabled(), user.getEmailVerifiedAt(), user.getLastLoginAt(),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    private static List<RoleCode> sortRoles(List<RoleCode> roles) {
        return roles.stream().sorted(Comparator.comparing(Enum::name)).toList();
    }
}
