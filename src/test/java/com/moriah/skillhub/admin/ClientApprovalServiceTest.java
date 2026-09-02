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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The ADMIN side of client self-registration — the review queue plus approve / reject state
 * transitions and their emails. */
@ExtendWith(MockitoExtension.class)
class ClientApprovalServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ClientApprovalService service;

    private User pendingClient;
    private Client clientRow;

    @BeforeEach
    void setUp() {
        pendingClient = new User();
        pendingClient.setId(42L);
        pendingClient.setUuid("11111111-1111-1111-1111-111111111111");
        pendingClient.setFullName("Jane Doe");
        pendingClient.setEmail("jane@acme.com");
        pendingClient.setPhone("919900001111");
        pendingClient.setStatus(UserStatus.PENDING_APPROVAL);

        clientRow = new Client();
        clientRow.setCompanyName("Acme Corp");
        clientRow.setIndustry("Retail");
        clientRow.setStatus(ClientStatus.INACTIVE);
        clientRow.setUser(pendingClient);
    }

    @Test
    void list_returnsPendingClientsWithCompanyInfo() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.search(UserStatus.PENDING_APPROVAL, RoleCode.CLIENT, pageable))
                .thenReturn(new PageImpl<>(List.of(pendingClient)));
        when(clientRepository.findByUserIdIn(List.of(42L))).thenReturn(List.of(clientRow));

        PageResponse<ClientRequestResponse> page = service.list(null, pageable);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).companyName()).isEqualTo("Acme Corp");
        assertThat(page.content().get(0).status()).isEqualTo(UserStatus.PENDING_APPROVAL);
    }

    @Test
    void list_rejectsAStatusOutsidePendingOrRejected() {
        assertThatThrownBy(() -> service.list(UserStatus.ACTIVE, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void approve_activatesUserAndClientRowAndEmails() {
        when(userRepository.findByUuid(pendingClient.getUuid())).thenReturn(Optional.of(pendingClient));
        when(clientRepository.findByUserId(42L)).thenReturn(Optional.of(clientRow));

        ClientRequestResponse response = service.approve(pendingClient.getUuid(), 999L);

        assertThat(pendingClient.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(pendingClient.getEmailVerifiedAt()).isNotNull();
        assertThat(clientRow.getStatus()).isEqualTo(ClientStatus.ACTIVE);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(pendingClient);
        verify(clientRepository).save(clientRow);
        verify(notificationService).enqueueAfterCommit(eq(42L), eq(NotificationChannel.EMAIL),
                eq("CLIENT_REGISTRATION_APPROVED"), any());
        verify(auditLogService).record(eq(999L), eq("CLIENT_REGISTRATION_APPROVED"), eq("User"), eq(42L), any(), any());
    }

    @Test
    void approve_nonPendingAccount_throwsConflict() {
        pendingClient.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByUuid(pendingClient.getUuid())).thenReturn(Optional.of(pendingClient));

        assertThatThrownBy(() -> service.approve(pendingClient.getUuid(), 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLIENT_REQUEST_NOT_PENDING);
        verify(userRepository, never()).save(any());
    }

    @Test
    void approve_unknownUuid_throwsNotFound() {
        when(userRepository.findByUuid("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve("missing", 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reject_setsRejectedAndEmailsReason() {
        when(userRepository.findByUuid(pendingClient.getUuid())).thenReturn(Optional.of(pendingClient));
        when(clientRepository.findByUserId(42L)).thenReturn(Optional.of(clientRow));

        ClientRequestResponse response = service.reject(pendingClient.getUuid(), "Not a fit right now", 999L);

        assertThat(pendingClient.getStatus()).isEqualTo(UserStatus.REJECTED);
        assertThat(response.status()).isEqualTo(UserStatus.REJECTED);
        assertThat(clientRow.getStatus()).isEqualTo(ClientStatus.INACTIVE);
        verify(notificationService).enqueueAfterCommit(eq(42L), eq(NotificationChannel.EMAIL),
                eq("CLIENT_REGISTRATION_REJECTED"), any());
        verify(auditLogService).record(eq(999L), eq("CLIENT_REGISTRATION_REJECTED"), eq("User"), eq(42L), any(), any());
    }
}
