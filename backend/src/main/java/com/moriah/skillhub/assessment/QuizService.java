package com.moriah.skillhub.assessment;

import com.moriah.skillhub.assessment.dto.AnswerResultView;
import com.moriah.skillhub.assessment.dto.AssessmentResponse;
import com.moriah.skillhub.assessment.dto.AssessmentResultRow;
import com.moriah.skillhub.assessment.dto.CreateAssessmentFromBankRequest;
import com.moriah.skillhub.assessment.dto.CreateAssessmentRequest;
import com.moriah.skillhub.assessment.dto.CreateQuestionRequest;
import com.moriah.skillhub.assessment.dto.MyAssessmentAttemptRow;
import com.moriah.skillhub.assessment.dto.QuizAttemptResponse;
import com.moriah.skillhub.assessment.dto.SubmitAnswerRequest;
import com.moriah.skillhub.assessment.dto.SubmitAttemptRequest;
import com.moriah.skillhub.assessment.entity.AttemptStatus;
import com.moriah.skillhub.assessment.entity.QuestionType;
import com.moriah.skillhub.assessment.entity.Quiz;
import com.moriah.skillhub.assessment.entity.QuizAnswer;
import com.moriah.skillhub.assessment.entity.QuizAttempt;
import com.moriah.skillhub.assessment.entity.QuestionBank;
import com.moriah.skillhub.assessment.entity.QuestionBankItem;
import com.moriah.skillhub.assessment.entity.QuizQuestion;
import com.moriah.skillhub.assessment.repository.QuestionBankItemRepository;
import com.moriah.skillhub.assessment.repository.QuestionBankRepository;
import com.moriah.skillhub.assessment.repository.QuizAnswerRepository;
import com.moriah.skillhub.assessment.repository.QuizAttemptRepository;
import com.moriah.skillhub.assessment.repository.QuizQuestionRepository;
import com.moriah.skillhub.assessment.repository.QuizRepository;
import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.project.ProjectService;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * build-plan.md feature 14. {@code BatchRepository} injected directly ({@code Batch} is the
 * established shared-kernel type, same pattern {@code SprintService}/{@code StandupService} use);
 * {@code BatchService} separately for the ownership/membership checks. {@link #startAttempt}
 * carries a normal {@code @Transactional} — like {@code AttendanceService#checkin}, it makes no
 * outbound HTTP call, so {@code QuizAttemptWriter}'s {@code REQUIRES_NEW} insert is all the
 * isolation the idempotent-start race actually needs.
 */
@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionBankItemRepository questionBankItemRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final QuizAttemptWriter quizAttemptWriter;
    private final QuizSubmissionWriter quizSubmissionWriter;
    private final GradingService gradingService;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ProjectService projectService;

    @Transactional
    public AssessmentResponse create(Long callerUserId, CreateAssessmentRequest request) {
        Batch batch = request.batchId() != null ? requireBatch(request.batchId()) : null;
        if (batch != null) {
            batchService.requireOwnerOrAdmin(callerUserId, batch);
        }
        // build-plan.md feature 15 verify line ("A DRAFT cannot attach to a task") applies
        // identically here — quizzes.project_id (V8) is the same "attach content to a project"
        // shape as tasks.project_id, closed once feature 15 existed to check against (deferred at
        // feature 14's own build time, tracked in progress-tracker.md's decision log).
        if (request.projectId() != null) {
            projectService.requirePublished(request.projectId());
        }
        User creator = requireUser(callerUserId);

        Quiz quiz = new Quiz();
        quiz.setProjectId(request.projectId());
        quiz.setBatch(batch);
        quiz.setTitle(request.title());
        quiz.setDurationMinutes(request.durationMinutes());
        quiz.setPassPercentage(request.passPercentage() != null
                ? request.passPercentage() : Constants.QUIZ_PASS_PERCENTAGE);
        quiz.setMaxAttempts(request.maxAttempts() != null
                ? request.maxAttempts() : Constants.QUIZ_DEFAULT_MAX_ATTEMPTS);
        quiz.setCreatedBy(creator);
        quiz.setActive(true);
        quizRepository.save(quiz);

        List<QuizQuestion> questions = request.questions().stream()
                .map(q -> toQuestionEntity(quiz, q))
                .toList();
        quizQuestionRepository.saveAll(questions);

        return toResponse(quiz);
    }

    @Transactional(readOnly = true)
    public PageResponse<AssessmentResponse> list(Long callerUserId, Long batchId, Pageable pageable) {
        if (batchId != null) {
            return PageResponse.from(quizRepository.findByBatchId(batchId, pageable).map(this::toResponse));
        }
        // batchId omitted — only TRAINER_PM / ADMIN may enumerate everything (the trainer's
        // publish / results screen); a student must always scope to a batch.
        List<String> roles = SecurityUtils.currentUserRoles();
        boolean author = roles.contains(RoleCode.ADMIN.name()) || roles.contains(RoleCode.TRAINER_PM.name());
        if (!author) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "batchId is required.");
        }
        return PageResponse.from(quizRepository.findAll(pageable).map(this::toResponse));
    }

    /**
     * Author-facing results feed ({@code GET /api/v1/assessments/results}) — one row per student
     * attempt, filterable by assessment, batch and cohort track. Read-only; no ownership narrowing
     * beyond the role gate, matching the developer→student publish flow (a DEVELOPER/TRAINER_PM/
     * ADMIN who can publish an assessment can see how it went).
     */
    @Transactional(readOnly = true)
    public PageResponse<AssessmentResultRow> results(Long assessmentId, Long batchId, String track,
                                                     boolean onlyFinished, Pageable pageable) {
        String trackFilter = (track == null || track.isBlank()) ? null : track;
        return PageResponse.from(quizAttemptRepository
                .searchResults(assessmentId, batchId, trackFilter, onlyFinished, pageable)
                .map(a -> {
                    Quiz q = a.getQuiz();
                    return new AssessmentResultRow(
                            a.getId(),
                            q.getId(),
                            q.getTitle(),
                            q.getBatch() == null ? null : q.getBatch().getId(),
                            q.getBatch() == null ? null : q.getBatch().getName(),
                            q.getBatch() == null ? null : q.getBatch().getTrackCode(),
                            a.getUser().getUuid(),
                            a.getUser().getFullName(),
                            a.getAttemptNumber(),
                            a.getStatus(),
                            a.getPercentage(),
                            a.getPassed(),
                            q.getPassPercentage(),
                            a.getSubmittedAt());
                }));
    }

    /**
     * The calling student's own attempts, newest first ({@code GET /api/v1/assessments/attempts/me}).
     * The student assessment list merges this by {@code assessmentId} so each row shows its real
     * state ("Completed — Passed 82%", "Failed", "In progress") rather than always offering a fresh
     * start. No answer keys or per-question detail — that stays on {@link #getAttempt}.
     */
    @Transactional(readOnly = true)
    public PageResponse<MyAssessmentAttemptRow> myAttempts(Long callerUserId, Pageable pageable) {
        return PageResponse.from(quizAttemptRepository.findMineByUser(callerUserId, pageable)
                .map(a -> {
                    Quiz q = a.getQuiz();
                    return new MyAssessmentAttemptRow(
                            a.getId(),
                            q.getId(),
                            q.getTitle(),
                            q.getBatch() == null ? null : q.getBatch().getId(),
                            a.getAttemptNumber(),
                            a.getStatus(),
                            a.getPercentage(),
                            a.getPassed(),
                            q.getPassPercentage(),
                            a.getSubmittedAt());
                }));
    }

    /**
     * Publish a question bank as a live, batch-scoped assessment. The bank's items — including
     * their {@code correctAnswer} keys — are snapshotted into fresh {@link QuizQuestion}s, so
     * editing the bank later leaves this assessment untouched. ADMIN may target any batch; a
     * TRAINER_PM is held to a batch they own, exactly as {@link #create}. {@code
     * request.questionCount()} lets a 200-question bank publish a random 30-question assessment
     * instead of dumping the whole bank on every attempt — {@code null} keeps the original
     * "every question" behavior.
     */
    @Transactional
    public AssessmentResponse createFromBank(Long callerUserId, CreateAssessmentFromBankRequest request) {
        QuestionBank bank = questionBankRepository.findById(request.bankId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUESTION_BANK_NOT_FOUND, request.bankId()));

        List<QuestionBankItem> items = new ArrayList<>(questionBankItemRepository
                .findByBankId(bank.getId(), Pageable.unpaged()).getContent());
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.QUESTION_BANK_EMPTY);
        }
        if (request.questionCount() != null) {
            if (request.questionCount() > items.size()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "This bank only has " + items.size() + " question(s) — questionCount can't exceed that.");
            }
            Collections.shuffle(items);
            items = items.subList(0, request.questionCount());
        }

        Batch batch = requireBatch(request.batchId());
        if (!SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name())) {
            batchService.requireOwnerOrAdmin(callerUserId, batch);
        }
        if (request.projectId() != null) {
            projectService.requirePublished(request.projectId());
        }
        User creator = requireUser(callerUserId);

        Quiz quiz = new Quiz();
        quiz.setProjectId(request.projectId());
        quiz.setBatch(batch);
        quiz.setTitle(request.title() != null && !request.title().isBlank() ? request.title() : bank.getName());
        quiz.setDurationMinutes(request.durationMinutes());
        quiz.setPassPercentage(request.passPercentage() != null
                ? request.passPercentage() : Constants.QUIZ_PASS_PERCENTAGE);
        quiz.setMaxAttempts(request.maxAttempts() != null
                ? request.maxAttempts() : Constants.QUIZ_DEFAULT_MAX_ATTEMPTS);
        quiz.setCreatedBy(creator);
        quiz.setActive(true);
        quizRepository.save(quiz);

        List<QuizQuestion> questions = items.stream().map(item -> {
            QuizQuestion q = new QuizQuestion();
            q.setQuiz(quiz);
            q.setQuestionText(item.getQuestionText());
            q.setQuestionType(item.getQuestionType());
            q.setOptions(item.getOptions());               // already a JSON string
            q.setCorrectAnswer(item.getCorrectAnswer());   // already a JSON string
            q.setMarks(item.getMarks());
            q.setExplanation(item.getExplanation());
            return q;
        }).toList();
        quizQuestionRepository.saveAll(questions);

        return toResponse(quiz);
    }

    /** Deactivate (never row-delete — attempt history must survive). ADMIN may deactivate any
     * assessment; a TRAINER_PM only one whose batch they own. Idempotent. */
    @Transactional
    public void deactivate(Long callerUserId, Long quizId) {
        Quiz quiz = requireQuiz(quizId);
        if (!SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name()) && quiz.getBatch() != null) {
            batchService.requireOwnerOrAdmin(callerUserId, quiz.getBatch());
        }
        quiz.setActive(false);
        quizRepository.save(quiz);
    }

    /** build-plan.md feature 14 "Sensible defaults" (implied by {@code max_attempts}): a student
     * re-calling this endpoint while an attempt is already {@code IN_PROGRESS} resumes it rather
     * than burning another attempt — checked before the max-attempts count or the writer at all. */
    @Transactional
    public QuizAttemptResponse startAttempt(Long callerUserId, Long quizId) {
        Quiz quiz = requireQuiz(quizId);
        if (!quiz.isActive()) {
            throw new BusinessException(ErrorCode.QUIZ_INACTIVE);
        }
        if (quiz.getBatch() != null && !batchService.isActiveMember(quiz.getBatch().getId(), callerUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_BATCH_MEMBER);
        }

        QuizAttempt existing = quizAttemptRepository.findInProgress(quizId, callerUserId).orElse(null);
        if (existing != null) {
            return toAttemptResponse(existing);
        }

        Integer maxAttemptNumber = quizAttemptRepository.findMaxAttemptNumber(quizId, callerUserId);
        int nextAttemptNumber = (maxAttemptNumber == null ? 0 : maxAttemptNumber) + 1;
        if (nextAttemptNumber > quiz.getMaxAttempts()) {
            throw new BusinessException(ErrorCode.QUIZ_MAX_ATTEMPTS_EXCEEDED);
        }

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setUser(requireUser(callerUserId));
        attempt.setAttemptNumber(nextAttemptNumber);
        attempt.setStartedAt(Instant.now());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);

        QuizAttempt saved;
        try {
            // QuizAttemptWriter, not quizAttemptRepository directly — see its own Javadoc.
            saved = quizAttemptWriter.tryInsert(attempt);
        } catch (DataIntegrityViolationException | CannotAcquireLockException e) {
            saved = quizAttemptWriter.findExisting(quizId, callerUserId, nextAttemptNumber).orElseThrow(() -> e);
        }

        return toAttemptResponse(saved);
    }

    @Transactional(readOnly = true)
    public QuizAttemptResponse getAttempt(Long callerUserId, Long attemptId) {
        QuizAttempt attempt = requireAttempt(attemptId);
        authorizeView(callerUserId, attempt);
        return toAttemptResponse(attempt);
    }

    /** Only the attempt's own student may submit it — unlike {@link #getAttempt}, no PM/Admin
     * bypass; nobody submits on a student's behalf. The actual grade-and-save happens on {@link
     * QuizSubmissionWriter}, a separate {@code REQUIRES_NEW} bean — see its own Javadoc for why: a
     * double-click or client retry can send two concurrent submits for the same attempt, both
     * passing the {@code IN_PROGRESS} check below before either commits. */
    @Transactional
    public QuizAttemptResponse submit(Long callerUserId, Long attemptId, SubmitAttemptRequest request) {
        QuizAttempt attempt = requireAttempt(attemptId);
        if (!attempt.getUser().getId().equals(callerUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.QUIZ_ATTEMPT_NOT_IN_PROGRESS);
        }

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderByIdAsc(attempt.getQuiz().getId());
        List<SubmitAnswerRequest> given = request.answers() != null ? request.answers() : List.of();
        Map<Long, SubmitAnswerRequest> byQuestionId = given.stream()
                .collect(Collectors.toMap(SubmitAnswerRequest::questionId, Function.identity(), (a, b) -> a));

        QuizSubmissionWriter.Result result;
        try {
            // QuizSubmissionWriter, not the repositories directly — see its own Javadoc.
            result = quizSubmissionWriter.trySubmit(attemptId, questions, byQuestionId);
            // code-standards.md "Service": "Audit every mutation that affects money, grades,
            // roles, or PIP status" — only the actual writer, not the race's recovery path (that
            // request wrote nothing of its own).
            auditLogService.record(callerUserId, "QUIZ_ATTEMPT_SUBMITTED", "QuizAttempt",
                    result.attempt().getId(), null, result.attempt());
        } catch (DataIntegrityViolationException | CannotAcquireLockException e) {
            result = quizSubmissionWriter.findExisting(attemptId);
        }

        // Answers come straight from the writer's own (already-committed) transaction, not a
        // fresh quizAnswerRepository.findByAttemptId call here — this method's own transaction
        // took its REPEATABLE READ snapshot back at requireAttempt(), before the writer's
        // transaction ever ran, so a query issued from here would silently see zero rows.
        return toAttemptResponse(result.attempt(), result.answers());
    }

    /** The owning student always sees their own attempt; otherwise {@code ADMIN} always can, and
     * a {@code TRAINER_PM} can only when the quiz is scoped to a batch they own — a project-level
     * quiz (no {@code batch}) has no PM to defer to, so anyone but the owner or an admin is
     * denied outright. */
    private void authorizeView(Long callerUserId, QuizAttempt attempt) {
        if (attempt.getUser().getId().equals(callerUserId)) {
            return;
        }
        if (SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name())) {
            return;
        }
        Batch batch = attempt.getQuiz().getBatch();
        if (batch == null) {
            throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        batchService.requireOwnerOrAdmin(callerUserId, batch);
    }

    private QuizQuestion toQuestionEntity(Quiz quiz, CreateQuestionRequest request) {
        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        question.setQuestionText(request.questionText());
        question.setQuestionType(request.questionType());
        question.setOptions(request.options() == null ? null : gradingService.toJson(request.options()));
        question.setCorrectAnswer(request.correctAnswerIndices() == null
                ? null : gradingService.toJson(request.correctAnswerIndices()));
        question.setMarks(request.marks());
        question.setExplanation(request.explanation());
        return question;
    }

    /** Never reads {@code QuizQuestion.correctAnswer} — build-plan.md feature 14: "Correct
     * answers never sent to the client." An {@code IN_PROGRESS} attempt has no persisted {@code
     * QuizAnswer} rows yet (nothing is written until {@link #submit}), so its question view comes
     * straight from the quiz's question set; a terminal attempt's view comes from the persisted,
     * already-graded answers instead — queried fresh here, safe for every caller except {@link
     * #submit} (see the other overload's Javadoc for why). */
    private QuizAttemptResponse toAttemptResponse(QuizAttempt attempt) {
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            List<AnswerResultView> questionViews = quizQuestionRepository
                    .findByQuizIdOrderByIdAsc(attempt.getQuiz().getId()).stream()
                    .map(this::toPreAnswerView)
                    .toList();
            return buildAttemptResponse(attempt, questionViews);
        }
        return toAttemptResponse(attempt, quizAnswerRepository.findByAttemptId(attempt.getId()));
    }

    /** Used only by {@link #submit} — {@code answers} must come from the same (already-committed)
     * transaction that wrote them, e.g. {@code QuizSubmissionWriter.Result}, never from a fresh
     * {@code quizAnswerRepository} query issued here: {@code submit}'s own transaction took its
     * MySQL REPEATABLE READ snapshot before the writer's separate {@code REQUIRES_NEW} transaction
     * ever ran, so a query from this method would silently see zero rows (confirmed the hard way —
     * see {@code QuizSubmissionWriter}'s Javadoc). A terminal attempt always has {@code answers},
     * even if empty (an expired/abandoned attempt), so this never falls into the pre-answer branch. */
    private QuizAttemptResponse toAttemptResponse(QuizAttempt attempt, List<QuizAnswer> answers) {
        List<AnswerResultView> questionViews = answers.stream().map(this::toResultView).toList();
        return buildAttemptResponse(attempt, questionViews);
    }

    private QuizAttemptResponse buildAttemptResponse(QuizAttempt attempt, List<AnswerResultView> questionViews) {
        Quiz quiz = attempt.getQuiz();
        return new QuizAttemptResponse(
                attempt.getId(),
                quiz.getId(),
                quiz.getTitle(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                quiz.getDurationMinutes(),
                questionViews,
                attempt.getAutoGradedMarks(),
                attempt.getAutoGradableMarks(),
                attempt.getPercentage(),
                attempt.getPassed());
    }

    private AnswerResultView toPreAnswerView(QuizQuestion question) {
        return new AnswerResultView(
                question.getId(),
                question.getQuestionText(),
                question.getQuestionType(),
                gradingService.parseOptions(question.getOptions()),
                question.getMarks(),
                null, null, null, null, null);
    }

    private AnswerResultView toResultView(QuizAnswer answer) {
        QuizQuestion question = answer.getQuestion();
        List<Integer> givenIndices = null;
        String givenCode = null;
        if (question.getQuestionType() == QuestionType.CODE) {
            givenCode = gradingService.parseCodeAnswer(answer.getGivenAnswer());
        } else {
            givenIndices = answer.getGivenAnswer() == null ? List.of() : gradingService.parseIndices(answer.getGivenAnswer());
        }
        return new AnswerResultView(
                question.getId(),
                question.getQuestionText(),
                question.getQuestionType(),
                gradingService.parseOptions(question.getOptions()),
                question.getMarks(),
                givenIndices,
                givenCode,
                answer.getIsCorrect(),
                answer.getMarksAwarded(),
                question.getExplanation());
    }

    private AssessmentResponse toResponse(Quiz quiz) {
        return new AssessmentResponse(
                quiz.getId(),
                quiz.getBatch() != null ? quiz.getBatch().getId() : null,
                quiz.getProjectId(),
                quiz.getTitle(),
                quiz.getDurationMinutes(),
                quiz.getPassPercentage(),
                quiz.getMaxAttempts(),
                quiz.isActive());
    }

    private Quiz requireQuiz(Long quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_NOT_FOUND, quizId));
    }

    private QuizAttempt requireAttempt(Long attemptId) {
        return quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND, attemptId));
    }

    private Batch requireBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, batchId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
    }
}
