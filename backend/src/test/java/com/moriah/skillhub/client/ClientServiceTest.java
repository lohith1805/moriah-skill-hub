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
import com.moriah.skillhub.user.repository.RoleRepository;
import com.moriah.skillhub.user.repository.UserRepository;
import com.moriah.skillhub.user.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** build-plan.md feature 21: "CLIENT users are provisioned by ADMIN." Covers {@link
 * ClientService#create}'s three branches — no portal login, portal login provisioned (user +
 * role + password-reset-token delegation, never a bespoke token mechanism), and the duplicate-
 * email guard — following this codebase's {@code CertificateServiceTest}/{@code
 * BatchServiceTest} Mockito conventions. */
@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private AuthService authService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ClientService clientService;

    @Test
    void create_withoutPortalLogin_savesClientWithNullUserAndNoRoleProvisioning() {
        CreateClientRequest request = new CreateClientRequest(
                "Acme Corp", "Jane Doe", "jane@example.com", "9999999999", "Retail", false);
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponse response = clientService.create(request, 1L);

        assertThat(response.companyName()).isEqualTo("Acme Corp");
        assertThat(response.userUuid()).isNull();
        assertThat(response.status()).isEqualTo(ClientStatus.ACTIVE);
        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
        verify(authService, never()).forgotPassword(any());
    }

    @Test
    void create_withPortalLogin_createsUserAssignsRoleAndTriggersPasswordReset() {
        CreateClientRequest request = new CreateClientRequest(
                "Acme Corp", "Jane Doe", "jane@example.com", "9999999999", "Retail", true);
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(42L);
            return user;
        });
        Role clientRole = new Role();
        clientRole.setId(7L);
        clientRole.setCode(RoleCode.CLIENT);
        when(roleRepository.findByCode(RoleCode.CLIENT)).thenReturn(Optional.of(clientRole));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponse response = clientService.create(request, 1L);

        assertThat(response.userUuid()).isNotNull();
        verify(userRoleRepository).save(any(UserRole.class));
        verify(authService).forgotPassword(new ForgotPasswordRequest("jane@example.com"));
        verify(auditLogService).record(eq(1L), eq("CLIENT_PORTAL_USER_PROVISIONED"), eq("User"), eq(42L), any(), any());
    }

    @Test
    void create_withPortalLoginAndDuplicateEmail_throwsEmailAlreadyRegistered() {
        CreateClientRequest request = new CreateClientRequest(
                "Acme Corp", "Jane Doe", "jane@example.com", null, null, true);
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> clientService.create(request, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_REGISTERED);
        verify(clientRepository, never()).save(any());
        verify(authService, never()).forgotPassword(any());
    }
}
