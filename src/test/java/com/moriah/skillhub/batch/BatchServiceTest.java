package com.moriah.skillhub.batch;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.entity.BatchStudent;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.batch.repository.BatchStudentRepository;
import com.moriah.skillhub.batch.repository.PendingBatchAllocationRepository;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import com.moriah.skillhub.subscription.EntitlementService;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** {@link BatchService#requireOwnerOrAdmin} was private and covered only by {@code
 * BatchFlowIT}'s real HTTP flow until feature 11 promoted it to public for reuse by {@code
 * SprintService}/{@code TaskService}; {@link BatchService#isActiveMember} is new this feature.
 * Both read {@code SecurityUtils.currentUserRoles()} off the real {@code SecurityContextHolder}
 * (no abstraction to mock), so this test populates and clears it directly, same as any Spring
 * Security-aware unit test would. */
@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock
    private BatchRepository batchRepository;
    @Mock
    private BatchStudentRepository batchStudentRepository;
    @Mock
    private PendingBatchAllocationRepository pendingBatchAllocationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EntitlementService entitlementService;

    @InjectMocks
    private BatchService batchService;

    private Batch batch;
    private User pm;

    @BeforeEach
    void setUp() {
        pm = new User();
        pm.setId(10L);
        batch = new Batch();
        batch.setId(100L);
        batch.setPm(pm);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireOwnerOrAdmin_callerIsThePm_doesNotThrow() {
        authenticateAs(10L, List.of("TRAINER_PM"));

        batchService.requireOwnerOrAdmin(10L, batch);
    }

    @Test
    void requireOwnerOrAdmin_callerIsAdmin_bypassesOwnership() {
        authenticateAs(99L, List.of("ADMIN"));

        batchService.requireOwnerOrAdmin(99L, batch);
    }

    @Test
    void requireOwnerOrAdmin_callerIsNeitherPmNorAdmin_throwsForbidden() {
        authenticateAs(77L, List.of("TRAINER_PM"));

        assertThatThrownBy(() -> batchService.requireOwnerOrAdmin(77L, batch))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void isActiveMember_activeEnrollment_returnsTrue() {
        BatchStudent batchStudent = new BatchStudent();
        batchStudent.setStatus(BatchStudentStatus.ACTIVE);
        when(batchStudentRepository.findByBatchIdAndUserId(100L, 5L)).thenReturn(Optional.of(batchStudent));

        assertThat(batchService.isActiveMember(100L, 5L)).isTrue();
    }

    @Test
    void isActiveMember_reassignedEnrollment_returnsFalse() {
        BatchStudent batchStudent = new BatchStudent();
        batchStudent.setStatus(BatchStudentStatus.REASSIGNED);
        when(batchStudentRepository.findByBatchIdAndUserId(100L, 5L)).thenReturn(Optional.of(batchStudent));

        assertThat(batchService.isActiveMember(100L, 5L)).isFalse();
    }

    @Test
    void isActiveMember_noEnrollmentAtAll_returnsFalse() {
        when(batchStudentRepository.findByBatchIdAndUserId(100L, 5L)).thenReturn(Optional.empty());

        assertThat(batchService.isActiveMember(100L, 5L)).isFalse();
    }

    private void authenticateAs(long userId, List<String> roles) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, "uuid-" + userId, roles);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }
}
