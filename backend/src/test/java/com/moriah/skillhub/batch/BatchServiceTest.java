package com.moriah.skillhub.batch;

import com.moriah.skillhub.batch.dto.AssignBatchProjectsRequest;
import com.moriah.skillhub.batch.dto.BatchProjectResponse;
import com.moriah.skillhub.batch.dto.GraduationResult;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.entity.BatchProject;
import com.moriah.skillhub.batch.entity.BatchStudent;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.batch.repository.BatchProjectRepository;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.batch.repository.BatchStudentRepository;
import com.moriah.skillhub.batch.repository.PendingBatchAllocationRepository;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import com.moriah.skillhub.project.entity.Project;
import com.moriah.skillhub.project.entity.ProjectStatus;
import com.moriah.skillhub.project.repository.ProjectRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private BatchProjectRepository batchProjectRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private com.moriah.skillhub.sprint.repository.TaskRepository taskRepository;

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
        batch.setTrackCode("FULL_STACK");
    }

    private Project project(long id, String title, ProjectStatus status, String track) {
        Project p = new Project();
        p.setId(id);
        p.setTitle(title);
        p.setSlug(title.toLowerCase().replace(" ", "-"));
        p.setStatus(status);
        p.setTrack(track);
        return p;
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
    void list_admin_usesTheFullList() {
        authenticateAs(99L, List.of("ADMIN"));
        when(entitlementService.planCodesById()).thenReturn(java.util.Map.of());
        when(batchRepository.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        batchService.list(99L, false, org.springframework.data.domain.PageRequest.of(0, 20));

        verify(batchRepository).findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
        verify(batchRepository, org.mockito.Mockito.never()).findByPmId(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void list_trainerPm_scopedToBatchesTheyOwn() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        when(entitlementService.planCodesById()).thenReturn(java.util.Map.of());
        when(batchRepository.findByPmId(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        batchService.list(10L, false, org.springframework.data.domain.PageRequest.of(0, 20));

        verify(batchRepository).findByPmId(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any());
        verify(batchRepository, org.mockito.Mockito.never()).findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
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
    void graduate_studentWithUnfinishedTask_throwsBusinessRuleViolation() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        User student = new User();
        student.setId(5L);
        student.setUuid("student-uuid");
        BatchStudent batchStudent = new BatchStudent();
        batchStudent.setStatus(BatchStudentStatus.ACTIVE);
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(batchStudentRepository.findByBatchIdAndUserId(100L, 5L)).thenReturn(Optional.of(batchStudent));
        when(taskRepository.countUnfinishedForStudentInBatch(eq(5L), eq(100L), any())).thenReturn(2L);

        assertThatThrownBy(() -> batchService.graduate(10L, 100L, "student-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
        assertThat(batchStudent.getStatus()).isEqualTo(BatchStudentStatus.ACTIVE);
        verify(batchRepository, times(0)).releaseSeat(100L);
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

    // ---- Assign Projects (curation, not visibility — ProjectService#list is untouched) ----

    @Test
    void assignProjects_publishedSameTrackProjects_replacesWholesale() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        Project p1 = project(1L, "Storefront API", ProjectStatus.PUBLISHED, "FULL_STACK");
        Project p2 = project(2L, "Order Service", ProjectStatus.PUBLISHED, "FULL_STACK");
        when(projectRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));
        // assignProjects returns listAssignedProjects' fresh read after the wholesale replace.
        when(batchProjectRepository.findProjectIdsByBatchId(100L)).thenReturn(List.of(1L, 2L));

        List<BatchProjectResponse> response = batchService.assignProjects(10L, 100L, new AssignBatchProjectsRequest(List.of(1L, 2L)));

        verify(batchProjectRepository).deleteByIdBatchId(100L);
        verify(batchProjectRepository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any(BatchProject.class));
        assertThat(response).extracting(BatchProjectResponse::title).containsExactlyInAnyOrder("Storefront API", "Order Service");
    }

    @Test
    void assignProjects_emptyList_clearsExistingAssignments() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

        batchService.assignProjects(10L, 100L, new AssignBatchProjectsRequest(List.of()));

        verify(batchProjectRepository).deleteByIdBatchId(100L);
        verify(batchProjectRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void assignProjects_notPublished_throwsValidationFailed() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        Project draft = project(1L, "Storefront API", ProjectStatus.DRAFT, "FULL_STACK");
        when(projectRepository.findAllById(List.of(1L))).thenReturn(List.of(draft));

        assertThatThrownBy(() -> batchService.assignProjects(10L, 100L, new AssignBatchProjectsRequest(List.of(1L))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        verify(batchProjectRepository, org.mockito.Mockito.never()).deleteByIdBatchId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void assignProjects_trackMismatch_throwsValidationFailed() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        Project wrongTrack = project(1L, "Churn Model", ProjectStatus.PUBLISHED, "DATA_ANALYTICS");
        when(projectRepository.findAllById(List.of(1L))).thenReturn(List.of(wrongTrack));

        assertThatThrownBy(() -> batchService.assignProjects(10L, 100L, new AssignBatchProjectsRequest(List.of(1L))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void assignProjects_unknownProjectId_throwsNotFound() {
        authenticateAs(10L, List.of("TRAINER_PM"));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(projectRepository.findAllById(List.of(404L))).thenReturn(List.of());

        assertThatThrownBy(() -> batchService.assignProjects(10L, 100L, new AssignBatchProjectsRequest(List.of(404L))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    void assignProjects_callerIsNotThePm_throwsForbidden() {
        authenticateAs(77L, List.of("TRAINER_PM"));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> batchService.assignProjects(77L, 100L, new AssignBatchProjectsRequest(List.of())))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(batchProjectRepository, org.mockito.Mockito.never()).deleteByIdBatchId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listAssignedProjects_returnsCuratedProjects() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(batchProjectRepository.findProjectIdsByBatchId(100L)).thenReturn(List.of(1L));
        when(projectRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(project(1L, "Storefront API", ProjectStatus.PUBLISHED, "FULL_STACK")));

        List<BatchProjectResponse> response = batchService.listAssignedProjects(100L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).title()).isEqualTo("Storefront API");
    }

    @Test
    void listAssignedProjects_none_returnsEmptyWithoutQueryingProjects() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(batchProjectRepository.findProjectIdsByBatchId(100L)).thenReturn(List.of());

        assertThat(batchService.listAssignedProjects(100L)).isEmpty();
        verify(projectRepository, org.mockito.Mockito.never()).findAllById(org.mockito.ArgumentMatchers.any());
    }

    private void authenticateAs(long userId, List<String> roles) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, "uuid-" + userId, roles);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }
}
