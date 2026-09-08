package com.moriah.skillhub.sprint;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.project.ProjectService;
import com.moriah.skillhub.sprint.dto.AssignTaskRequest;
import com.moriah.skillhub.sprint.dto.CreateTaskRequest;
import com.moriah.skillhub.sprint.dto.TaskResponse;
import com.moriah.skillhub.sprint.dto.UpdateTaskRequest;
import com.moriah.skillhub.sprint.entity.Sprint;
import com.moriah.skillhub.sprint.entity.SprintStatus;
import com.moriah.skillhub.sprint.entity.Task;
import com.moriah.skillhub.sprint.entity.TaskStatus;
import com.moriah.skillhub.sprint.entity.TaskType;
import com.moriah.skillhub.sprint.repository.TaskRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code BatchService}/{@code SprintService} are mocked — this class only proves {@code
 * TaskService}'s own logic: the state machine ({@code ALLOWED_TRANSITIONS}), the {@code
 * completed_points} rollup, and the eligibility checks {@code assign}/{@code pull} each run.
 * Every branch of the state machine build-plan.md feature 11 describes gets its own test. */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private SprintService sprintService;
    @Mock
    private BatchService batchService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TaskPullGuard taskPullGuard;
    @Mock
    private ProjectService projectService;

    @InjectMocks
    private TaskService taskService;

    private Batch batch;
    private Sprint sprint;
    private User student;

    @BeforeEach
    void setUp() {
        batch = new Batch();
        batch.setId(100L);
        sprint = new Sprint();
        sprint.setId(10L);
        sprint.setBatch(batch);
        sprint.setCompletedPoints(0);

        student = new User();
        student.setId(5L);
        student.setUuid("student-uuid");
        student.setFullName("Ada Lovelace");
    }

    @Test
    void create_happyPath_savesBacklogTask() {
        when(sprintService.requireSprint(10L)).thenReturn(sprint);
        CreateTaskRequest request = new CreateTaskRequest(10L, null, "Build the thing", "desc",
                TaskType.STORY, 5, null);

        TaskResponse response = taskService.create(1L, request);

        assertThat(response.status()).isEqualTo(TaskStatus.BACKLOG);
        assertThat(response.sprintId()).isEqualTo(10L);
        verify(batchService).requireOwnerOrAdmin(1L, batch);
        verify(taskRepository).save(any(Task.class));
    }

    /** build-plan.md feature 15 verify line: "A DRAFT cannot attach to a task." */
    @Test
    void create_withProjectId_requiresPublishedProject() {
        when(sprintService.requireSprint(10L)).thenReturn(sprint);
        CreateTaskRequest request = new CreateTaskRequest(10L, 55L, "Fix the bug", "desc",
                TaskType.BUGFIX, 3, null);

        taskService.create(1L, request);

        verify(projectService).requirePublished(55L);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void create_projectNotPublished_propagatesRejection() {
        when(sprintService.requireSprint(10L)).thenReturn(sprint);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.PROJECT_NOT_PUBLISHED))
                .when(projectService).requirePublished(55L);
        CreateTaskRequest request = new CreateTaskRequest(10L, 55L, "Fix the bug", "desc",
                TaskType.BUGFIX, 3, null);

        assertThatThrownBy(() -> taskService.create(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_NOT_PUBLISHED);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void assign_happyPath_setsAssigneeAndStatus() {
        Task task = task(1L, TaskStatus.BACKLOG);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(batchService.isActiveMember(100L, 5L)).thenReturn(true);

        TaskResponse response = taskService.assign(1L, 1L, new AssignTaskRequest("student-uuid"));

        assertThat(response.status()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(response.assignedToUuid()).isEqualTo("student-uuid");
        verify(batchService).requireOwnerOrAdmin(1L, batch);
    }

    @Test
    void assign_studentNotActiveBatchMember_throwsNotBatchMember() {
        Task task = task(1L, TaskStatus.BACKLOG);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(batchService.isActiveMember(100L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.assign(1L, 1L, new AssignTaskRequest("student-uuid")))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_BATCH_MEMBER);
    }

    @Test
    void assign_taskNotBacklog_throwsTaskInvalidTransition() {
        Task task = task(1L, TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(batchService.isActiveMember(100L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> taskService.assign(1L, 1L, new AssignTaskRequest("student-uuid")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TASK_INVALID_TRANSITION);
    }

    @Test
    void pull_happyPath_selfAssignsAndActivates() {
        Task task = task(1L, TaskStatus.BACKLOG);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(batchService.isActiveMember(100L, 5L)).thenReturn(true);
        when(taskPullGuard.blocksPull(5L)).thenReturn(false);
        when(userRepository.findById(5L)).thenReturn(Optional.of(student));

        TaskResponse response = taskService.pull(5L, 1L);

        assertThat(response.status()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(response.assignedToUuid()).isEqualTo("student-uuid");
    }

    @Test
    void pull_notActiveBatchMember_throwsNotBatchMember() {
        Task task = task(1L, TaskStatus.BACKLOG);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(batchService.isActiveMember(100L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.pull(5L, 1L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_BATCH_MEMBER);
        verify(taskPullGuard, never()).blocksPull(anyLong());
    }

    @Test
    void pull_taskNotBacklog_throwsTaskInvalidTransition() {
        Task task = task(1L, TaskStatus.ASSIGNED);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(batchService.isActiveMember(100L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> taskService.pull(5L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TASK_INVALID_TRANSITION);
        verify(taskPullGuard, never()).blocksPull(anyLong());
    }

    @Test
    void pull_blockedByOpenPipRecord_throwsTaskPullBlockedByPip() {
        Task task = task(1L, TaskStatus.BACKLOG);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(batchService.isActiveMember(100L, 5L)).thenReturn(true);
        when(taskPullGuard.blocksPull(5L)).thenReturn(true);

        assertThatThrownBy(() -> taskService.pull(5L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TASK_PULL_BLOCKED_BY_PIP);
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void update_backlogToAssignedViaPut_rejectedInFavorOfAssignOrPull() {
        Task task = task(1L, TaskStatus.BACKLOG);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.update(1L, 1L, updateRequest(TaskStatus.ASSIGNED)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TASK_INVALID_TRANSITION);
    }

    @Test
    void update_skippedState_throwsTaskInvalidTransition() {
        Task task = task(1L, TaskStatus.ASSIGNED);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.update(1L, 1L, updateRequest(TaskStatus.IN_REVIEW)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TASK_INVALID_TRANSITION);
    }

    @Test
    void update_legalForwardTransition_updatesStatus() {
        Task task = task(1L, TaskStatus.ASSIGNED);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskResponse response = taskService.update(1L, 1L, updateRequest(TaskStatus.IN_PROGRESS));

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void update_toCompleted_rollsUpStoryPointsOntoSprint() {
        // storyPoints comes from the PUT request itself (fields are applied together before the
        // status transition runs), not whatever the task carried beforehand.
        Task task = task(1L, TaskStatus.IN_REVIEW);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskResponse response = taskService.update(1L, 1L, updateRequest(TaskStatus.COMPLETED, 8));

        assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
        assertThat(sprint.getCompletedPoints()).isEqualTo(8);
    }

    @Test
    void update_toCompletedWithNoStoryPoints_addsZero() {
        Task task = task(1L, TaskStatus.IN_REVIEW);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        taskService.update(1L, 1L, updateRequest(TaskStatus.COMPLETED, null));

        assertThat(sprint.getCompletedPoints()).isZero();
    }

    @Test
    void update_toRejected_doesNotTouchSprintPoints() {
        Task task = task(1L, TaskStatus.IN_REVIEW);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskResponse response = taskService.update(1L, 1L, updateRequest(TaskStatus.REJECTED, 8));

        assertThat(response.status()).isEqualTo(TaskStatus.REJECTED);
        assertThat(sprint.getCompletedPoints()).isZero();
    }

    @Test
    void update_taskNotFound_throwsResourceNotFound() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(1L, 99L, updateRequest(TaskStatus.IN_PROGRESS)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_withAssignedToUuid_resolvesToUserIdBeforeSearching() {
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(taskRepository.search(eq(10L), eq(TaskStatus.BACKLOG), eq(5L), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        taskService.list(10L, TaskStatus.BACKLOG, "student-uuid",
                org.springframework.data.domain.Pageable.unpaged());

        verify(taskRepository).search(eq(10L), eq(TaskStatus.BACKLOG), eq(5L), any());
    }

    @Test
    void start_ownAssignedTask_movesToInProgress() {
        Task task = task(1L, TaskStatus.ASSIGNED);
        task.setAssignedTo(student);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskResponse response = taskService.start(5L, 1L);

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void start_notTheAssignee_throwsNotResourceOwner() {
        Task task = task(1L, TaskStatus.ASSIGNED);
        task.setAssignedTo(student); // owned by user 5
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.start(999L, 1L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
    }

    @Test
    void start_taskStillBacklog_throwsTaskInvalidTransition() {
        Task task = task(1L, TaskStatus.BACKLOG);
        task.setAssignedTo(student);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.start(5L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TASK_INVALID_TRANSITION);
    }

    /** {@code IN_REVIEW -> IN_PROGRESS} is a legal transition in {@code ALLOWED_TRANSITIONS} (the
     * changes-requested path), but {@code start} must not let a student pull their own task back
     * out of the PM's review queue. */
    @Test
    void start_taskInReview_throwsTaskInvalidTransition() {
        Task task = task(1L, TaskStatus.IN_REVIEW);
        task.setAssignedTo(student);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.start(5L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TASK_INVALID_TRANSITION);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_REVIEW);
    }

    @Test
    void listMine_studentInNoBatch_returnsEmptyWithoutQueryingTasks() {
        when(batchService.activeBatchIdsForUser(5L)).thenReturn(java.util.List.of());

        var page = taskService.listMine(5L, org.springframework.data.domain.Pageable.unpaged());

        assertThat(page.content()).isEmpty();
        verify(taskRepository, never()).findMyBoard(any(), anyLong(), any());
    }

    @Test
    void listMine_scopesToTheCallersActiveBatches() {
        when(batchService.activeBatchIdsForUser(5L)).thenReturn(java.util.List.of(100L, 101L));
        when(taskRepository.findMyBoard(eq(java.util.List.of(100L, 101L)), eq(5L), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        taskService.listMine(5L, org.springframework.data.domain.Pageable.unpaged());

        verify(taskRepository).findMyBoard(eq(java.util.List.of(100L, 101L)), eq(5L), any());
    }

    private UpdateTaskRequest updateRequest(TaskStatus status) {
        return updateRequest(status, 3);
    }

    private UpdateTaskRequest updateRequest(TaskStatus status, Integer storyPoints) {
        return new UpdateTaskRequest("Title", "Description", TaskType.STORY, storyPoints, null, status);
    }

    private Task task(Long id, TaskStatus status) {
        Task task = new Task();
        task.setId(id);
        task.setSprint(sprint);
        task.setTitle("Existing task");
        task.setTaskType(TaskType.STORY);
        task.setStatus(status);
        return task;
    }
}
