package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.AdminUserResponse;
import com.moriah.skillhub.admin.dto.UpdateUserRolesRequest;
import com.moriah.skillhub.admin.dto.UpdateUserStatusRequest;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
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

    private List<RoleCode> currentRoles(Long userId) {
        return userRoleRepository.findRoleCodesByUserId(userId);
    }

    private User requireUser(String userUuid) {
        return userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userUuid));
    }

    private AdminUserResponse toResponse(User user, List<RoleCode> roles) {
        List<RoleCode> sortedRoles = roles.stream().sorted(Comparator.comparing(Enum::name)).toList();
        return new AdminUserResponse(
                user.getUuid(), user.getFullName(), user.getEmail(), user.getStatus(), sortedRoles, user.getCreatedAt());
    }
}
