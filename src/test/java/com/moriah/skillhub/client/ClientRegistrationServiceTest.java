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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

/** Client self-registration ({@code POST /api/v1/auth/register/client}) — the account lands
 * PENDING_APPROVAL with the chosen password, the linked clients row is INACTIVE, and an
 * "application received" email is queued. Mirrors {@link ClientServiceTest}'s Mockito style. */
@ExtendWith(MockitoExtension.class)
class ClientRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ClientRegistrationService service;

    private ClientRegisterRequest request() {
        return new ClientRegisterRequest("Jane Doe", "jane@acme.com", "919900001111",
                "Password123!", "Acme Corp", "Retail");
    }

    @Test
    void register_createsPendingUserInactiveClientAndQueuesEmail() {
        when(userRepository.existsByEmail("jane@acme.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });
        Role clientRole = new Role();
        clientRole.setId(7L);
        clientRole.setCode(RoleCode.CLIENT);
        when(roleRepository.findByCode(RoleCode.CLIENT)).thenReturn(Optional.of(clientRole));
        when(passwordEncoder.encode("Password123!")).thenReturn("$2a$hash");
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response = service.register(request());

        assertThat(response.email()).isEqualTo("jane@acme.com");
        assertThat(response.uuid()).isNotNull();

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        assertThat(userCap.getValue().getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
        assertThat(userCap.getValue().getPasswordHash()).isEqualTo("$2a$hash");
        assertThat(userCap.getValue().getEmailVerifiedAt()).isNull();

        ArgumentCaptor<Client> clientCap = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(clientCap.capture());
        assertThat(clientCap.getValue().getStatus()).isEqualTo(ClientStatus.INACTIVE);
        assertThat(clientCap.getValue().getContactPerson()).isEqualTo("Jane Doe");
        assertThat(clientCap.getValue().getCompanyName()).isEqualTo("Acme Corp");

        verify(userRoleRepository).save(any(UserRole.class));
        verify(notificationService).enqueueAfterCommit(eq(42L), eq(NotificationChannel.EMAIL),
                eq("CLIENT_REGISTRATION_RECEIVED"), any());
        verify(auditLogService).record(eq(42L), eq("CLIENT_REGISTRATION_SUBMITTED"), eq("User"), eq(42L), any(), any());
    }

    @Test
    void register_duplicateEmail_throwsAndWritesNothing() {
        when(userRepository.existsByEmail("jane@acme.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_REGISTERED);

        verify(userRepository, never()).save(any());
        verify(clientRepository, never()).save(any());
        verify(notificationService, never()).enqueueAfterCommit(any(), any(), any(), any());
    }
}
