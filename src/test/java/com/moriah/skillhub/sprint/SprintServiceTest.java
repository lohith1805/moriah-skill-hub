package com.moriah.skillhub.sprint;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.sprint.dto.CreateSprintRequest;
import com.moriah.skillhub.sprint.dto.SprintResponse;
import com.moriah.skillhub.sprint.dto.UpdateSprintRequest;
import com.moriah.skillhub.sprint.dto.VelocityProjection;
import com.moriah.skillhub.sprint.dto.SprintTaskStatusCount;
import com.moriah.skillhub.sprint.entity.Sprint;
import com.moriah.skillhub.sprint.entity.SprintStatus;
import com.moriah.skillhub.sprint.entity.TaskStatus;
import com.moriah.skillhub.sprint.repository.SprintRepository;
import com.moriah.skillhub.sprint.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code BatchService} is mocked entirely — its own ownership logic is covered by {@code
 * BatchServiceTest}; this class only proves {@code SprintService} delegates to it and reacts
 * correctly to what it returns/throws. Every branch of {@code transitionStatus} (build-plan.md
 * feature 11: "Illegal transition returns 409") gets its own test. */
@ExtendWith(MockitoExtension.class)
class SprintServiceTest {

    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private BatchService batchService;
    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private SprintService sprintService;

    private Batch batch;

    @BeforeEach
    void setUp() {
        batch = new Batch();
        batch.setId(100L);
    }

    @Test
    void create_happyPath_savesPlannedSprint() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(sprintRepository.existsByBatchIdAndSprintNumber(100L, 1)).thenReturn(false);
        when(sprintRepository.existsOverlapping(eq(100L), isNull(), any(), any())).thenReturn(false);

        SprintResponse response = sprintService.create(1L, request(100L, 1));

        assertThat(response.sprintNumber()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(SprintStatus.PLANNED);
        verify(batchService).requireOwnerOrAdmin(1L, batch);
        verify(sprintRepository).save(any(Sprint.class));
    }

    @Test
    void create_batchNotFound_throwsResourceNotFound() {
        when(batchRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sprintService.create(1L, request(100L, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_duplicateSprintNumber_throwsSprintNumberTaken() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(sprintRepository.existsByBatchIdAndSprintNumber(100L, 1)).thenReturn(true);

        assertThatThrownBy(() -> sprintService.create(1L, request(100L, 1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SPRINT_NUMBER_TAKEN);
    }

    @Test
    void create_overlappingDates_throwsSprintDateOverlap() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(sprintRepository.existsByBatchIdAndSprintNumber(100L, 1)).thenReturn(false);
        when(sprintRepository.existsOverlapping(eq(100L), isNull(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> sprintService.create(1L, request(100L, 1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SPRINT_DATE_OVERLAP);
    }

    @Test
    void activate_firstSprintNoPredecessor_activatesDirectly() {
        Sprint sprint = sprint(1L, 1, SprintStatus.PLANNED);
        when(sprintRepository.findById(1L)).thenReturn(Optional.of(sprint));
        when(sprintRepository.findByBatchIdAndStatus(100L, SprintStatus.ACTIVE)).thenReturn(Optional.empty());

        SprintResponse response = sprintService.activate(1L, 1L);

        assertThat(response.status()).isEqualTo(SprintStatus.ACTIVE);
    }

    @Test
    void activate_anotherSprintAlreadyActiveInBatch_throwsSprintAlreadyActive() {
        Sprint sprint = sprint(1L, 1, SprintStatus.PLANNED);
        Sprint other = sprint(2L, 2, SprintStatus.ACTIVE);
        when(sprintRepository.findById(1L)).thenReturn(Optional.of(sprint));
        when(sprintRepository.findByBatchIdAndStatus(100L, SprintStatus.ACTIVE)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> sprintService.activate(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SPRINT_ALREADY_ACTIVE);
    }

    @Test
    void activate_previousSprintNotCompleted_throwsSprintPreviousNotCompleted() {
        // requirePreviousSprintCompleted now runs before requireNoOtherActiveSprint
        // (SprintService.transitionStatus) precisely so this throws before
        // findByBatchIdAndStatus is ever called — no stub needed for it here.
        Sprint sprint = sprint(2L, 2, SprintStatus.PLANNED);
        Sprint previous = sprint(1L, 1, SprintStatus.PLANNED);
        when(sprintRepository.findById(2L)).thenReturn(Optional.of(sprint));
        when(sprintRepository.findByBatchIdAndSprintNumber(100L, 1)).thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> sprintService.activate(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SPRINT_PREVIOUS_NOT_COMPLETED);
    }

    @Test
    void activate_previousSprintMissing_throwsSprintPreviousNotCompleted() {
        Sprint sprint = sprint(2L, 2, SprintStatus.PLANNED);
        when(sprintRepository.findById(2L)).thenReturn(Optional.of(sprint));
        when(sprintRepository.findByBatchIdAndSprintNumber(100L, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sprintService.activate(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SPRINT_PREVIOUS_NOT_COMPLETED);
    }

    @Test
    void activate_previousSprintCompleted_activatesSuccessfully() {
        Sprint sprint = sprint(2L, 2, SprintStatus.PLANNED);
        Sprint previous = sprint(1L, 1, SprintStatus.COMPLETED);
        when(sprintRepository.findById(2L)).thenReturn(Optional.of(sprint));
        when(sprintRepository.findByBatchIdAndStatus(100L, SprintStatus.ACTIVE)).thenReturn(Optional.empty());
        when(sprintRepository.findByBatchIdAndSprintNumber(100L, 1)).thenReturn(Optional.of(previous));

        SprintResponse response = sprintService.activate(1L, 2L);

        assertThat(response.status()).isEqualTo(SprintStatus.ACTIVE);
    }

    @Test
    void activate_sprintNotPlanned_throwsSprintInvalidTransition() {
        Sprint sprint = sprint(1L, 1, SprintStatus.COMPLETED);
        when(sprintRepository.findById(1L)).thenReturn(Optional.of(sprint));

        assertThatThrownBy(() -> sprintService.activate(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SPRINT_INVALID_TRANSITION);
    }

    @Test
    void update_dateOverlapExcludesSelf_passesOwnIdToExistsOverlapping() {
        Sprint sprint = sprint(5L, 3, SprintStatus.PLANNED);
        when(sprintRepository.findById(5L)).thenReturn(Optional.of(sprint));
        when(sprintRepository.existsOverlapping(eq(100L), eq(5L), any(), any())).thenReturn(false);

        sprintService.update(1L, 5L, updateRequest(SprintStatus.PLANNED));

        verify(sprintRepository).existsOverlapping(eq(100L), eq(5L), any(), any());
    }

    @Test
    void update_sameStatus_doesNotRunTransitionChecks() {
        Sprint sprint = sprint(5L, 3, SprintStatus.PLANNED);
        when(sprintRepository.findById(5L)).thenReturn(Optional.of(sprint));
        when(sprintRepository.existsOverlapping(eq(100L), eq(5L), any(), any())).thenReturn(false);

        SprintResponse response = sprintService.update(1L, 5L, updateRequest(SprintStatus.PLANNED));

        assertThat(response.status()).isEqualTo(SprintStatus.PLANNED);
        verify(sprintRepository, never()).findByBatchIdAndStatus(anyLong(), any());
    }

    @Test
    void update_illegalBackwardTransition_throwsSprintInvalidTransition() {
        Sprint sprint = sprint(5L, 1, SprintStatus.ACTIVE);
        when(sprintRepository.findById(5L)).thenReturn(Optional.of(sprint));
        when(sprintRepository.existsOverlapping(eq(100L), eq(5L), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> sprintService.update(1L, 5L, updateRequest(SprintStatus.PLANNED)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SPRINT_INVALID_TRANSITION);
    }

    @Test
    void update_activeToCompleted_transitionsSuccessfully() {
        Sprint sprint = sprint(5L, 1, SprintStatus.ACTIVE);
        when(sprintRepository.findById(5L)).thenReturn(Optional.of(sprint));
        when(sprintRepository.existsOverlapping(eq(100L), eq(5L), any(), any())).thenReturn(false);

        SprintResponse response = sprintService.update(1L, 5L, updateRequest(SprintStatus.COMPLETED));

        assertThat(response.status()).isEqualTo(SprintStatus.COMPLETED);
    }

    @Test
    void totalVelocity_sumsCompletedPointsAcrossCompletedSprints() {
        when(sprintRepository.findVelocity(100L)).thenReturn(List.of(
                new VelocityProjection(1L, 1, 20, 18),
                new VelocityProjection(2L, 2, 15, 15)));

        assertThat(sprintService.totalVelocity(100L)).isEqualTo(33);
    }

    private CreateSprintRequest request(Long batchId, int sprintNumber) {
        return new CreateSprintRequest(batchId, sprintNumber, "Sprint goal",
                LocalDate.now(), LocalDate.now().plusDays(7), 20);
    }

    private UpdateSprintRequest updateRequest(SprintStatus status) {
        return new UpdateSprintRequest("Updated goal", LocalDate.now(), LocalDate.now().plusDays(7), 15, status);
    }

    private Sprint sprint(Long id, int sprintNumber, SprintStatus status) {
        Sprint sprint = new Sprint();
        sprint.setId(id);
        sprint.setBatch(batch);
        sprint.setSprintNumber(sprintNumber);
        sprint.setStatus(status);
        sprint.setStartDate(LocalDate.now());
        sprint.setEndDate(LocalDate.now().plusDays(7));
        sprint.setCompletedPoints(0);
        return sprint;
    }

    // ---- taskStatusCountsForBatch ----

    @Test
    void taskStatusCountsForBatch_groupsCountsBySprintThenStatus() {
        when(taskRepository.countTaskStatusesForBatch(100L)).thenReturn(List.of(
                new SprintTaskStatusCount(1L, TaskStatus.COMPLETED, 5L),
                new SprintTaskStatusCount(1L, TaskStatus.IN_PROGRESS, 2L),
                new SprintTaskStatusCount(2L, TaskStatus.BACKLOG, 3L)));

        var result = sprintService.taskStatusCountsForBatch(100L);

        assertThat(result).hasSize(2);
        assertThat(result.get(1L)).containsEntry(TaskStatus.COMPLETED, 5L).containsEntry(TaskStatus.IN_PROGRESS, 2L);
        assertThat(result.get(2L)).containsEntry(TaskStatus.BACKLOG, 3L);
        assertThat(result.get(2L)).doesNotContainKey(TaskStatus.COMPLETED);
    }

    @Test
    void taskStatusCountsForBatch_noTasks_returnsEmptyMap() {
        when(taskRepository.countTaskStatusesForBatch(100L)).thenReturn(List.of());

        assertThat(sprintService.taskStatusCountsForBatch(100L)).isEmpty();
    }
}
