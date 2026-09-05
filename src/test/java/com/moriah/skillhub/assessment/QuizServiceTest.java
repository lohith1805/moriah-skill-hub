package com.moriah.skillhub.assessment;

import com.moriah.skillhub.assessment.dto.AssessmentResponse;
import com.moriah.skillhub.assessment.dto.CreateAssessmentRequest;
import com.moriah.skillhub.assessment.dto.CreateQuestionRequest;
import com.moriah.skillhub.assessment.dto.QuizAttemptResponse;
import com.moriah.skillhub.assessment.dto.SubmitAnswerRequest;
import com.moriah.skillhub.assessment.dto.SubmitAttemptRequest;
import com.moriah.skillhub.assessment.entity.AttemptStatus;
import com.moriah.skillhub.assessment.entity.QuestionType;
import com.moriah.skillhub.assessment.entity.Quiz;
import com.moriah.skillhub.assessment.entity.QuizAttempt;
import com.moriah.skillhub.assessment.entity.QuizQuestion;
import com.moriah.skillhub.assessment.dto.CreateAssessmentFromBankRequest;
import com.moriah.skillhub.assessment.entity.QuestionBank;
import com.moriah.skillhub.assessment.entity.QuestionBankItem;
import com.moriah.skillhub.assessment.repository.QuestionBankItemRepository;
import com.moriah.skillhub.assessment.repository.QuestionBankRepository;
import com.moriah.skillhub.assessment.repository.QuizAnswerRepository;
import com.moriah.skillhub.assessment.repository.QuizAttemptRepository;
import com.moriah.skillhub.assessment.repository.QuizQuestionRepository;
import com.moriah.skillhub.assessment.repository.QuizRepository;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code BatchService}/{@code GradingService} are mocked entirely — their own logic has its own
 * coverage; this class only proves {@code QuizService} delegates to them correctly. {@code
 * QuizAttemptWriter}/{@code QuizSubmissionWriter} are mocked too (package-private, same
 * package). */
@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private QuizRepository quizRepository;
    @Mock
    private QuizQuestionRepository quizQuestionRepository;
    @Mock
    private QuestionBankRepository questionBankRepository;
    @Mock
    private QuestionBankItemRepository questionBankItemRepository;
    @Mock
    private QuizAttemptRepository quizAttemptRepository;
    @Mock
    private QuizAnswerRepository quizAnswerRepository;
    @Mock
    private QuizAttemptWriter quizAttemptWriter;
    @Mock
    private QuizSubmissionWriter quizSubmissionWriter;
    @Mock
    private GradingService gradingService;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private BatchService batchService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private com.moriah.skillhub.project.ProjectService projectService;

    @InjectMocks
    private QuizService quizService;

    private Batch batch;
    private User student;

    @BeforeEach
    void setUp() {
        batch = new Batch();
        batch.setId(100L);
        student = new User();
        student.setId(7L);
    }

    @Test
    void create_batchScoped_checksOwnershipAndSavesQuestions() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(gradingService.toJson(any())).thenReturn("[]");

        AssessmentResponse response = quizService.create(1L, createRequest(100L));

        assertThat(response.title()).isEqualTo("Java Basics");
        verify(batchService).requireOwnerOrAdmin(1L, batch);
        verify(quizRepository).save(any(Quiz.class));
        verify(quizQuestionRepository).saveAll(any());
    }

    @Test
    void create_projectScopedNoBatch_skipsOwnershipCheck() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(gradingService.toJson(any())).thenReturn("[]");

        quizService.create(1L, createRequest(null));

        verify(batchService, org.mockito.Mockito.never()).requireOwnerOrAdmin(any(), any());
    }

    @Test
    void createFromBank_asAdmin_snapshotsBankAndSkipsOwnershipCheck() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new AuthenticatedPrincipal(1L, "uuid-1", List.of("ADMIN")), null));
        try {
            QuestionBank bank = new QuestionBank();
            bank.setId(5L);
            bank.setName("Java Basics Bank");
            QuestionBankItem item = new QuestionBankItem();
            item.setQuestionText("2 + 2 = ?");
            item.setQuestionType(QuestionType.MCQ);
            item.setOptions("[\"3\",\"4\"]");
            item.setCorrectAnswer("[1]");
            item.setMarks(1);

            when(questionBankRepository.findById(5L)).thenReturn(Optional.of(bank));
            when(questionBankItemRepository.findByBankId(eq(5L), any()))
                    .thenReturn(new PageImpl<>(List.of(item)));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
            when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));

            AssessmentResponse response = quizService.createFromBank(1L,
                    new CreateAssessmentFromBankRequest(5L, 100L, null, null, 20, null, null, null));

            assertThat(response.title()).isEqualTo("Java Basics Bank"); // defaults to bank name
            verify(batchService, org.mockito.Mockito.never()).requireOwnerOrAdmin(any(), any());
            verify(quizQuestionRepository).saveAll(any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createFromBank_emptyBank_throwsConflict() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new AuthenticatedPrincipal(1L, "uuid-1", List.of("ADMIN")), null));
        try {
            QuestionBank bank = new QuestionBank();
            bank.setId(5L);
            when(questionBankRepository.findById(5L)).thenReturn(Optional.of(bank));
            when(questionBankItemRepository.findByBankId(eq(5L), any())).thenReturn(new PageImpl<>(List.of()));

            assertThatThrownBy(() -> quizService.createFromBank(1L,
                    new CreateAssessmentFromBankRequest(5L, 100L, null, null, 20, null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUESTION_BANK_EMPTY);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createFromBank_questionCountLessThanBankSize_snapshotsOnlyThatMany() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new AuthenticatedPrincipal(1L, "uuid-1", List.of("ADMIN")), null));
        try {
            QuestionBank bank = new QuestionBank();
            bank.setId(5L);
            bank.setName("Java Basics Bank");
            List<QuestionBankItem> items = new java.util.ArrayList<>();
            for (int i = 0; i < 200; i++) {
                QuestionBankItem item = new QuestionBankItem();
                item.setQuestionText("Question " + i);
                item.setQuestionType(QuestionType.MCQ);
                item.setOptions("[\"a\",\"b\"]");
                item.setCorrectAnswer("[0]");
                item.setMarks(1);
                items.add(item);
            }

            when(questionBankRepository.findById(5L)).thenReturn(Optional.of(bank));
            when(questionBankItemRepository.findByBankId(eq(5L), any())).thenReturn(new PageImpl<>(items));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
            when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));

            ArgumentCaptor<List<QuizQuestion>> captor = ArgumentCaptor.forClass(List.class);
            quizService.createFromBank(1L, new CreateAssessmentFromBankRequest(5L, 100L, null, null, 20, null, null, 30));

            verify(quizQuestionRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(30);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createFromBank_questionCountExceedsBankSize_throwsValidationFailed() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new AuthenticatedPrincipal(1L, "uuid-1", List.of("ADMIN")), null));
        try {
            QuestionBank bank = new QuestionBank();
            bank.setId(5L);
            QuestionBankItem item = new QuestionBankItem();
            item.setQuestionType(QuestionType.MCQ);
            when(questionBankRepository.findById(5L)).thenReturn(Optional.of(bank));
            when(questionBankItemRepository.findByBankId(eq(5L), any())).thenReturn(new PageImpl<>(List.of(item)));

            assertThatThrownBy(() -> quizService.createFromBank(1L,
                    new CreateAssessmentFromBankRequest(5L, 100L, null, null, 20, null, null, 5)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
            verify(quizQuestionRepository, org.mockito.Mockito.never()).saveAll(any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void startAttempt_existingInProgress_resumesInsteadOfCreatingNew() {
        Quiz quiz = quiz(1L, true, 3);
        QuizAttempt existing = attempt(quiz, 1);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(quizAttemptRepository.findInProgress(1L, 7L)).thenReturn(Optional.of(existing));
        when(quizQuestionRepository.findByQuizIdOrderByIdAsc(1L)).thenReturn(List.of());

        QuizAttemptResponse response = quizService.startAttempt(7L, 1L);

        assertThat(response.attemptNumber()).isEqualTo(1);
        verify(quizAttemptWriter, org.mockito.Mockito.never()).tryInsert(any());
    }

    @Test
    void startAttempt_inactiveQuiz_throwsQuizInactive() {
        Quiz quiz = quiz(1L, false, 3);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.startAttempt(7L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUIZ_INACTIVE);
    }

    @Test
    void startAttempt_notActiveBatchMember_throwsForbidden() {
        Quiz quiz = quiz(1L, true, 3);
        quiz.setBatch(batch);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(batchService.isActiveMember(100L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> quizService.startAttempt(7L, 1L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_BATCH_MEMBER);
    }

    @Test
    void startAttempt_maxAttemptsExceeded_throws409() {
        Quiz quiz = quiz(1L, true, 2);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(quizAttemptRepository.findInProgress(1L, 7L)).thenReturn(Optional.empty());
        when(quizAttemptRepository.findMaxAttemptNumber(1L, 7L)).thenReturn(2);

        assertThatThrownBy(() -> quizService.startAttempt(7L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUIZ_MAX_ATTEMPTS_EXCEEDED);
    }

    @Test
    void startAttempt_firstAttempt_createsAttemptNumberOne() {
        Quiz quiz = quiz(1L, true, 3);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(quizAttemptRepository.findInProgress(1L, 7L)).thenReturn(Optional.empty());
        when(quizAttemptRepository.findMaxAttemptNumber(1L, 7L)).thenReturn(null);
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));
        when(quizAttemptWriter.tryInsert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quizQuestionRepository.findByQuizIdOrderByIdAsc(1L)).thenReturn(List.of());

        QuizAttemptResponse response = quizService.startAttempt(7L, 1L);

        assertThat(response.attemptNumber()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }

    @Test
    void getAttempt_ownerStudent_allowed() {
        Quiz quiz = quiz(1L, true, 3);
        QuizAttempt attempt = attempt(quiz, 1);
        attempt.setUser(student);
        when(quizAttemptRepository.findById(5L)).thenReturn(Optional.of(attempt));
        when(quizQuestionRepository.findByQuizIdOrderByIdAsc(1L)).thenReturn(List.of());

        QuizAttemptResponse response = quizService.getAttempt(7L, 5L);

        assertThat(response.id()).isEqualTo(attempt.getId());
    }

    @Test
    void getAttempt_notOwnerNoBatch_throwsForbidden() {
        Quiz quiz = quiz(1L, true, 3); // no batch set — project-scoped
        QuizAttempt attempt = attempt(quiz, 1);
        attempt.setUser(student);
        when(quizAttemptRepository.findById(5L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> quizService.getAttempt(99L, 5L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    void getAttempt_pmOwningBatch_allowed() {
        Quiz quiz = quiz(1L, true, 3);
        quiz.setBatch(batch);
        QuizAttempt attempt = attempt(quiz, 1);
        attempt.setUser(student);
        when(quizAttemptRepository.findById(5L)).thenReturn(Optional.of(attempt));
        when(quizAnswerRepository.findByAttemptId(5L)).thenReturn(List.of());
        attempt.setStatus(AttemptStatus.SUBMITTED);

        quizService.getAttempt(1L, 5L);

        verify(batchService).requireOwnerOrAdmin(1L, batch);
    }

    @Test
    void submit_notOwner_throwsForbidden() {
        Quiz quiz = quiz(1L, true, 3);
        QuizAttempt attempt = attempt(quiz, 1);
        attempt.setUser(student);
        when(quizAttemptRepository.findById(5L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> quizService.submit(99L, 5L, new SubmitAttemptRequest(List.of())))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    void submit_alreadySubmitted_throws409() {
        Quiz quiz = quiz(1L, true, 3);
        QuizAttempt attempt = attempt(quiz, 1);
        attempt.setUser(student);
        attempt.setStatus(AttemptStatus.SUBMITTED);
        when(quizAttemptRepository.findById(5L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> quizService.submit(7L, 5L, new SubmitAttemptRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUIZ_ATTEMPT_NOT_IN_PROGRESS);
    }

    @Test
    void submit_happyPath_delegatesToWriterAndAudits() {
        Quiz quiz = quiz(1L, true, 3);
        QuizAttempt attempt = attempt(quiz, 1);
        attempt.setUser(student);
        when(quizAttemptRepository.findById(5L)).thenReturn(Optional.of(attempt));
        when(quizQuestionRepository.findByQuizIdOrderByIdAsc(1L)).thenReturn(List.of());

        QuizAttempt submitted = attempt(quiz, 1);
        submitted.setUser(student);
        submitted.setStatus(AttemptStatus.SUBMITTED);
        when(quizSubmissionWriter.trySubmit(eq(5L), any(), any()))
                .thenReturn(new QuizSubmissionWriter.Result(submitted, List.of()));

        SubmitAttemptRequest request = new SubmitAttemptRequest(
                List.of(new SubmitAnswerRequest(1L, List.of(0), null)));
        QuizAttemptResponse response = quizService.submit(7L, 5L, request);

        assertThat(response.status()).isEqualTo(AttemptStatus.SUBMITTED);
        verify(quizSubmissionWriter).trySubmit(eq(5L), any(), any());
        verify(auditLogService).record(eq(7L), eq("QUIZ_ATTEMPT_SUBMITTED"), eq("QuizAttempt"), eq(5L), any(), any());
    }

    @Test
    void submit_quizHasCodeQuestions_flagsPendingManualGrading() {
        Quiz quiz = quiz(1L, true, 3);
        QuizAttempt attempt = attempt(quiz, 1);
        attempt.setUser(student);
        when(quizAttemptRepository.findById(5L)).thenReturn(Optional.of(attempt));
        when(quizQuestionRepository.findByQuizIdOrderByIdAsc(1L)).thenReturn(List.of());

        QuizAttempt submitted = attempt(quiz, 1);
        submitted.setUser(student);
        submitted.setStatus(AttemptStatus.PENDING_MANUAL_GRADING);
        when(quizSubmissionWriter.trySubmit(eq(5L), any(), any()))
                .thenReturn(new QuizSubmissionWriter.Result(submitted, List.of()));

        QuizAttemptResponse response = quizService.submit(7L, 5L, new SubmitAttemptRequest(List.of()));

        assertThat(response.status()).isEqualTo(AttemptStatus.PENDING_MANUAL_GRADING);
    }

    /** Regression test mirroring {@code startAttempt_existingInProgress_resumesInsteadOfCreatingNew}
     * for the submit-side race: a concurrent double-submit's loser recovers the winner's
     * already-committed result via {@code QuizSubmissionWriter.findExisting} instead of the raw
     * {@code DataIntegrityViolationException} propagating as a 500 — and does not double-audit an
     * action this request didn't actually perform. */
    @Test
    void submit_concurrentConflict_recoversExistingResultWithoutDoubleAuditing() {
        Quiz quiz = quiz(1L, true, 3);
        QuizAttempt attempt = attempt(quiz, 1);
        attempt.setUser(student);
        when(quizAttemptRepository.findById(5L)).thenReturn(Optional.of(attempt));
        when(quizQuestionRepository.findByQuizIdOrderByIdAsc(1L)).thenReturn(List.of());

        QuizAttempt winner = attempt(quiz, 1);
        winner.setUser(student);
        winner.setStatus(AttemptStatus.SUBMITTED);
        when(quizSubmissionWriter.trySubmit(eq(5L), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate quiz_answers row"));
        when(quizSubmissionWriter.findExisting(5L))
                .thenReturn(new QuizSubmissionWriter.Result(winner, List.of()));

        QuizAttemptResponse response = quizService.submit(7L, 5L, new SubmitAttemptRequest(List.of()));

        assertThat(response.status()).isEqualTo(AttemptStatus.SUBMITTED);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    private CreateAssessmentRequest createRequest(Long batchId) {
        return new CreateAssessmentRequest(batchId, null, "Java Basics", 30, null, null,
                List.of(new CreateQuestionRequest("What is 2+2?", QuestionType.MCQ,
                        List.of("3", "4", "5"), List.of(1), 10, null)));
    }

    private Quiz quiz(Long id, boolean active, int maxAttempts) {
        Quiz quiz = new Quiz();
        quiz.setId(id);
        quiz.setTitle("Java Basics");
        quiz.setDurationMinutes(30);
        quiz.setPassPercentage(60);
        quiz.setMaxAttempts(maxAttempts);
        quiz.setActive(active);
        return quiz;
    }

    private QuizAttempt attempt(Quiz quiz, int attemptNumber) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(5L);
        attempt.setQuiz(quiz);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        return attempt;
    }
}
