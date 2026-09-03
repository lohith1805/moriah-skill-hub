package com.moriah.skillhub.batch;

import com.moriah.skillhub.batch.dto.GraduationResult;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.entity.BatchStudent;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.batch.repository.BatchStudentRepository;
import com.moriah.skillhub.batch.repository.PendingBatchAllocationRepository;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    void list_studentScoped_usesTheEnrolledOnlyQuery() {
        when(entitlementService.planCodesById()).thenReturn(java.util.Map.of());
        when(batchRepository.findEnrolledByUserId(org.mockito.ArgumentMatchers.eq(50L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        batchService.list(50L, true, org.springframework.data.domain.PageRequest.of(0, 20));

        verify(batchRepository).findEnrolledByUserId(org.mockito.ArgumentMatchers.eq(50L), org.mockito.ArgumentMatchers.any());
        verify(batchRepository, org.mockito.Mockito.never()).findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void list_notStudentScoped_usesTheFullList() {
        when(entitlementService.planCodesById()).thenReturn(java.util.Map.of());
        when(batchRepository.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        batchService.list(1L, false, org.springframework.data.domain.PageRequest.of(0, 20));

        verify(batchRepository).findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void get_studentScoped_notEnrolled_throwsNotFound() {
        when(batchStudentRepository.findByBatchIdAndUserId(100L, 50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchService.get(100L, 50L, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BATCH_NOT_FOUND);
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

    @Test
    void graduate_activeStudent_setsGraduatedAndReleasesSeat() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        User student = new User();
        student.setId(5L);
        student.setUuid("student-uuid");
        student.setFullName("Ada Lovelace");
        BatchStudent batchStudent = new BatchStudent();
        batchStudent.setStatus(BatchStudentStatus.ACTIVE);
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(batchStudentRepository.findByBatchIdAndUserId(100L, 5L)).thenReturn(Optional.of(batchStudent));
        when(userRepository.getReferenceById(10L)).thenReturn(pm);

        GraduationResult result = batchService.graduate(10L, 100L, "student-uuid");

        assertThat(result.userId()).isEqualTo(5L);
        assertThat(result.userFullName()).isEqualTo("Ada Lovelace");
        assertThat(result.graduatedAt()).isNotNull();
        assertThat(batchStudent.getStatus()).isEqualTo(BatchStudentStatus.GRADUATED);
        assertThat(batchStudent.getGraduatedAt()).isEqualTo(result.graduatedAt());
        assertThat(batchStudent.getGraduatedBy()).isEqualTo(pm);
        verify(batchRepository, times(1)).releaseSeat(100L);
    }

    @Test
    void graduate_studentOnPip_throwsBusinessRuleViolation() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        User student = new User();
        student.setId(5L);
        student.setUuid("student-uuid");
        BatchStudent batchStudent = new BatchStudent();
        batchStudent.setStatus(BatchStudentStatus.ON_PIP);
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(batchStudentRepository.findByBatchIdAndUserId(100L, 5L)).thenReturn(Optional.of(batchStudent));

        assertThatThrownBy(() -> batchService.graduate(10L, 100L, "student-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(batchRepository, times(0)).releaseSeat(100L);
    }

    @Test
    void graduate_alreadyGraduated_throwsBusinessRuleViolation() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        User student = new User();
        student.setId(5L);
        student.setUuid("student-uuid");
        BatchStudent batchStudent = new BatchStudent();
        batchStudent.setStatus(BatchStudentStatus.GRADUATED);
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(batchStudentRepository.findByBatchIdAndUserId(100L, 5L)).thenReturn(Optional.of(batchStudent));

        assertThatThrownBy(() -> batchService.graduate(10L, 100L, "student-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void graduate_studentNotEnrolled_throwsNotFound() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        User student = new User();
        student.setId(5L);
        student.setUuid("student-uuid");
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(batchStudentRepository.findByBatchIdAndUserId(100L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchService.graduate(10L, 100L, "student-uuid"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BATCH_STUDENT_NOT_FOUND);
    }

    @Test
    void graduate_callerNotOwnerNorAdmin_throwsForbidden() {
        authenticateAs(77L, List.of("TRAINER_PM"));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.graduate(77L, 100L, "student-uuid"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void hasGraduatedFromBatch_graduatedInThisBatch_returnsTrue() {
        BatchStudent batchStudent = new BatchStudent();
        batchStudent.setStatus(BatchStudentStatus.GRADUATED);
        when(batchStudentRepository.findByBatchIdAndUserId(100L, 5L)).thenReturn(Optional.of(batchStudent));

        assertThat(batchService.hasGraduatedFromBatch(100L, 5L)).isTrue();
    }

    @Test
    void hasGraduatedFromBatch_activeInThisBatch_returnsFalse() {
        BatchStudent batchStudent = new BatchStudent();
        batchStudent.setStatus(BatchStudentStatus.ACTIVE);
        when(batchStudentRepository.findByBatchIdAndUserId(100L, 5L)).thenReturn(Optional.of(batchStudent));

        assertThat(batchService.hasGraduatedFromBatch(100L, 5L)).isFalse();
    }

    @Test
    void listStudents_callerOwnsBatch_returnsMappedRoster() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

        User student = new User();
        student.setUuid("stu-uuid-1");
        student.setFullName("Sam Student");
        student.setEmail("sam@moriah.test");
        BatchStudent row = new BatchStudent();
        row.setUser(student);
        row.setStatus(BatchStudentStatus.ACTIVE);
        when(batchStudentRepository.findByBatchIdOrderByJoinedAtAscIdAsc(100L)).thenReturn(List.of(row));

        var roster = batchService.listStudents(10L, 100L);

        assertThat(roster).singleElement()
                .satisfies(r -> {
                    assertThat(r.userUuid()).isEqualTo("stu-uuid-1");
                    assertThat(r.fullName()).isEqualTo("Sam Student");
                    assertThat(r.email()).isEqualTo("sam@moriah.test");
                    assertThat(r.status()).isEqualTo(BatchStudentStatus.ACTIVE);
                });
    }

    @Test
    void listStudents_callerIsNotThePm_throwsForbidden() {
        authenticateAs(77L, List.of("TRAINER_PM"));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.listStudents(77L, 100L))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private void authenticateAs(long userId, List<String> roles) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, "uuid-" + userId, roles);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }
}
