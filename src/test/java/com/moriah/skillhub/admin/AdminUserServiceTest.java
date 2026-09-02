package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.AdminUserResponse;
import com.moriah.skillhub.admin.dto.CreateStaffRequest;
import com.moriah.skillhub.admin.dto.UpdateUserRolesRequest;
import com.moriah.skillhub.admin.dto.UpdateUserStatusRequest;
import com.moriah.skillhub.auth.AuthService;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** build-plan.md feature 22's own "Verify" line demands proof that suspension/role-change
 * increment {@code token_version} — the mechanism itself is exercised here in isolation (no HTTP,
 * no real revocation check); the actual "a stale token is rejected within a second" proof is
 * {@code AdminMetricsFlowIT}, over real HTTP against a real JWT. */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AuthService authService;

    @InjectMocks
    private AdminUserService adminUserService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
        user.setUuid("11111111-1111-1111-1111-111111111111");
        user.setFullName("Target User");
        user.setEmail("target@example.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(3);
    }

    @Test
    void updateStatus_bumpsTokenVersionAndSaves() {
        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(userRoleRepository.findRoleCodesByUserId(42L)).thenReturn(List.of(RoleCode.STUDENT));

        AdminUserResponse response = adminUserService.updateStatus(
                user.getUuid(), new UpdateUserStatusRequest(UserStatus.SUSPENDED), 999L);

        assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.getTokenVersion()).isEqualTo(4);
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository).save(user);
        verify(auditLogService).record(eq(999L), eq("USER_STATUS_CHANGED"), eq("User"), eq(42L), any(), any());
    }

    @Test
    void updateStatus_unknownUuid_throwsNotFound() {
        when(userRepository.findByUuid("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.updateStatus("missing", new UpdateUserStatusRequest(UserStatus.SUSPENDED), 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRoles_bumpsTokenVersionReplacesRolesAndSaves() {
        Role adminRole = new Role();
        adminRole.setId(7L);
        adminRole.setCode(RoleCode.ADMIN);

        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(userRoleRepository.findRoleCodesByUserId(42L)).thenReturn(List.of(RoleCode.STUDENT));
        when(roleRepository.findByCode(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));

        AdminUserResponse response = adminUserService.updateRoles(
                user.getUuid(), new UpdateUserRolesRequest(Set.of(RoleCode.ADMIN)), 999L);

        assertThat(response.roles()).containsExactly(RoleCode.ADMIN);
        assertThat(user.getTokenVersion()).isEqualTo(4);
        verify(userRoleRepository).deleteByIdUserId(42L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRoleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);

        verify(userRepository).save(user);
        verify(auditLogService).record(eq(999L), eq("USER_ROLES_CHANGED"), eq("User"), eq(42L), any(), any());
    }

    @Test
    void inviteStaff_createsInvitedAccountAssignsRolesAndSendsInvite() {
        Role trainerRole = new Role();
        trainerRole.setId(2L);
        trainerRole.setCode(RoleCode.TRAINER_PM);
        when(userRepository.existsByEmail("trainer@example.com")).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.TRAINER_PM)).thenReturn(Optional.of(trainerRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(50L);
            return u;
        });

        AdminUserResponse response = adminUserService.inviteStaff(
                new CreateStaffRequest("New Trainer", "trainer@example.com", "919000000000", Set.of(RoleCode.TRAINER_PM)),
                999L);

        assertThat(response.status()).isEqualTo(UserStatus.INVITED);
        assertThat(response.roles()).containsExactly(RoleCode.TRAINER_PM);

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        assertThat(userCap.getValue().getStatus()).isEqualTo(UserStatus.INVITED);
        assertThat(userCap.getValue().getPasswordHash()).isNull();

        verify(userRoleRepository).saveAll(any());
        verify(authService).issueStaffInvite(any(User.class));
        verify(auditLogService).record(eq(999L), eq("STAFF_INVITED"), eq("User"), eq(50L), any(), any());
    }

    @Test
    void inviteStaff_withSelfServiceRole_isRejected() {
        assertThatThrownBy(() -> adminUserService.inviteStaff(
                new CreateStaffRequest("X", "x@example.com", null, Set.of(RoleCode.STUDENT)), 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROLE_NOT_STAFF_ASSIGNABLE);

        verify(userRepository, never()).save(any());
        verify(authService, never()).issueStaffInvite(any());
    }

    @Test
    void resendStaffInvite_forNonInvitedAccount_throwsConflict() {
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.resendStaffInvite(user.getUuid(), 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_NOT_PENDING);

        verify(authService, never()).issueStaffInvite(any());
    }
}
