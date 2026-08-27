package com.moriah.skillhub.submission;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.submission.dto.CreateWeeklyReviewRequest;
import com.moriah.skillhub.submission.dto.WeeklyReviewResponse;
import com.moriah.skillhub.submission.entity.WeeklyRating;
import com.moriah.skillhub.submission.entity.WeeklyReview;
import com.moriah.skillhub.submission.repository.WeeklyReviewRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** build-plan.md feature 12: "This is the data source for the REVIEW_FAILED PIP rule" — proves
 * an {@code UNSATISFACTORY} rating is genuinely queryable per student per week, and that a
 * second {@code POST} for the same week updates rather than duplicates (the record's own
 * Javadoc: no dedicated {@code PUT} endpoint exists for a correction). */
@ExtendWith(MockitoExtension.class)
class WeeklyReviewServiceTest {

    @Mock
    private WeeklyReviewRepository weeklyReviewRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private BatchService batchService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WeeklyReviewService weeklyReviewService;

    private User student;
    private User pm;
    private Batch batch;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(1L);
        student.setUuid("uuid-student");
        student.setFullName("Student One");

        pm = new User();
        pm.setId(2L);
        pm.setUuid("uuid-pm");

        batch = new Batch();
        batch.setId(5L);
    }

    @Test
    void create_noExistingRow_createsANewOne() {
        LocalDate weekStart = LocalDate.of(2026, 9, 7);
        when(userRepository.findByUuid("uuid-student")).thenReturn(Optional.of(student));
        when(userRepository.findById(2L)).thenReturn(Optional.of(pm));
        when(batchRepository.findById(5L)).thenReturn(Optional.of(batch));
        when(weeklyReviewRepository.findByUserIdAndWeekStart(1L, weekStart)).thenReturn(Optional.empty());
        when(weeklyReviewRepository.save(org.mockito.ArgumentMatchers.any(WeeklyReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateWeeklyReviewRequest request = new CreateWeeklyReviewRequest(
                "uuid-student", 5L, 3L, weekStart, WeeklyRating.UNSATISFACTORY, "Missed three standups");

        WeeklyReviewResponse response = weeklyReviewService.create(2L, request);

        assertThat(response.rating()).isEqualTo(WeeklyRating.UNSATISFACTORY);
        assertThat(response.studentUuid()).isEqualTo("uuid-student");
        assertThat(response.reviewedByUuid()).isEqualTo("uuid-pm");
        verify(weeklyReviewRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void create_existingRowForSameWeek_updatesRatherThanDuplicating() {
        LocalDate weekStart = LocalDate.of(2026, 9, 7);
        WeeklyReview existing = new WeeklyReview();
        existing.setId(77L);
        existing.setUser(student);
        existing.setBatch(batch);
        existing.setSprintId(3L);
        existing.setWeekStart(weekStart);
        existing.setRating(WeeklyRating.SATISFACTORY);
        existing.setReviewedBy(pm);

        when(userRepository.findByUuid("uuid-student")).thenReturn(Optional.of(student));
        when(userRepository.findById(2L)).thenReturn(Optional.of(pm));
        when(batchRepository.findById(5L)).thenReturn(Optional.of(batch));
        when(weeklyReviewRepository.findByUserIdAndWeekStart(1L, weekStart)).thenReturn(Optional.of(existing));
        when(weeklyReviewRepository.save(org.mockito.ArgumentMatchers.any(WeeklyReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateWeeklyReviewRequest request = new CreateWeeklyReviewRequest(
                "uuid-student", 5L, 3L, weekStart, WeeklyRating.NEEDS_IMPROVEMENT, "Corrected rating");

        WeeklyReviewResponse response = weeklyReviewService.create(2L, request);

        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.rating()).isEqualTo(WeeklyRating.NEEDS_IMPROVEMENT);
        assertThat(response.notes()).isEqualTo("Corrected rating");
    }

    /** `/review` finding: a PM rating a student outside their own batch was never rejected —
     * {@code batchService.requireOwnerOrAdmin} is the same check {@code SprintService.create}/
     * {@code TaskService.completeReview} already enforce for a batch-scoped write. */
    @Test
    void create_callerNotBatchOwnerOrAdmin_throwsForbiddenAndNeverWrites() {
        LocalDate weekStart = LocalDate.of(2026, 9, 7);
        when(userRepository.findByUuid("uuid-student")).thenReturn(Optional.of(student));
        when(userRepository.findById(2L)).thenReturn(Optional.of(pm));
        when(batchRepository.findById(5L)).thenReturn(Optional.of(batch));
        org.mockito.Mockito.doThrow(new ForbiddenOperationException(ErrorCode.NOT_BATCH_OWNER))
                .when(batchService).requireOwnerOrAdmin(2L, batch);

        CreateWeeklyReviewRequest request = new CreateWeeklyReviewRequest(
                "uuid-student", 5L, 3L, weekStart, WeeklyRating.UNSATISFACTORY, "Missed three standups");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> weeklyReviewService.create(2L, request))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(weeklyReviewRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
