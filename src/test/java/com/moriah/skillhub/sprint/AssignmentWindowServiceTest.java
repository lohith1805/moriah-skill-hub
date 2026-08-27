package com.moriah.skillhub.sprint;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.sprint.dto.AssignmentWindowResponse;
import com.moriah.skillhub.sprint.dto.CreateAssignmentWindowRequest;
import com.moriah.skillhub.sprint.entity.Sprint;
import com.moriah.skillhub.sprint.entity.Task;
import com.moriah.skillhub.sprint.repository.AssignmentWindowRepository;
import com.moriah.skillhub.sprint.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentWindowServiceTest {

    @Mock
    private AssignmentWindowRepository assignmentWindowRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private BatchService batchService;
    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private AssignmentWindowService assignmentWindowService;

    private Batch batch;
    private Batch otherBatch;

    @BeforeEach
    void setUp() {
        batch = new Batch();
        batch.setId(100L);
        otherBatch = new Batch();
        otherBatch.setId(200L);
    }

    @Test
    void create_happyPath_savesWindowWithNoLinkedTask() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(assignmentWindowRepository.existsByBatchIdAndWeekStart(100L, LocalDate.of(2026, 9, 1))).thenReturn(false);

        AssignmentWindowResponse response = assignmentWindowService.create(1L, request(null));

        assertThat(response.batchId()).isEqualTo(100L);
        assertThat(response.taskId()).isNull();
    }

    @Test
    void create_batchNotFound_throwsResourceNotFound() {
        when(batchRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentWindowService.create(1L, request(null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_duplicateWeek_throwsAssignmentWindowWeekTaken() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(assignmentWindowRepository.existsByBatchIdAndWeekStart(100L, LocalDate.of(2026, 9, 1))).thenReturn(true);

        assertThatThrownBy(() -> assignmentWindowService.create(1L, request(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ASSIGNMENT_WINDOW_WEEK_TAKEN);
    }

    @Test
    void create_linkedTaskInSameBatch_attachesTask() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(assignmentWindowRepository.existsByBatchIdAndWeekStart(100L, LocalDate.of(2026, 9, 1))).thenReturn(false);
        Task task = taskInBatch(batch);
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        AssignmentWindowResponse response = assignmentWindowService.create(1L, request(7L));

        assertThat(response.taskId()).isEqualTo(task.getId());
    }

    @Test
    void create_linkedTaskInDifferentBatch_throwsAssignmentWindowTaskWrongBatch() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(assignmentWindowRepository.existsByBatchIdAndWeekStart(100L, LocalDate.of(2026, 9, 1))).thenReturn(false);
        Task task = taskInBatch(otherBatch);
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> assignmentWindowService.create(1L, request(7L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ASSIGNMENT_WINDOW_TASK_WRONG_BATCH);
    }

    private CreateAssignmentWindowRequest request(Long taskId) {
        return new CreateAssignmentWindowRequest(100L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7),
                Instant.parse("2026-09-07T18:00:00Z"), taskId);
    }

    private Task taskInBatch(Batch owningBatch) {
        Sprint sprint = new Sprint();
        sprint.setId(1L);
        sprint.setBatch(owningBatch);
        Task task = new Task();
        task.setId(7L);
        task.setSprint(sprint);
        return task;
    }
}
