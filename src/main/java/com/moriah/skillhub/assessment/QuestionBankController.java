package com.moriah.skillhub.assessment;

import com.moriah.skillhub.assessment.dto.AddBankQuestionRequest;
import com.moriah.skillhub.assessment.dto.CreateQuestionBankRequest;
import com.moriah.skillhub.assessment.dto.QuestionBankItemResponse;
import com.moriah.skillhub.assessment.dto.QuestionBankResponse;
import com.moriah.skillhub.assessment.dto.UpdateQuestionBankRequest;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assessment question bank (gap B1.15). {@code /api/v1/assessments/banks}, TRAINER_PM/ADMIN —
 * the same mutation roles {@code AssessmentController} uses for quiz creation.
 */
@RestController
@RequestMapping("/api/v1/assessments/banks")
@RequiredArgsConstructor
@Tag(name = "Assessments")
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Create a question bank")
    public ResponseEntity<ApiResponse<QuestionBankResponse>> createBank(
            @Valid @RequestBody CreateQuestionBankRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(questionBankService.createBank(request, callerUserId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "List question banks — optional topic / active filters")
    public ResponseEntity<ApiResponse<PageResponse<QuestionBankResponse>>> listBanks(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(questionBankService.listBanks(topic, active, pageable)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Rename / re-topic a bank or toggle its active flag")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bank updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No bank with this id")
    })
    public ResponseEntity<ApiResponse<QuestionBankResponse>> updateBank(
            @PathVariable Long id, @Valid @RequestBody UpdateQuestionBankRequest request) {
        return ResponseEntity.ok(ApiResponse.success(questionBankService.updateBank(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Deactivate a bank (is_active = false) — never row-deletes")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bank deactivated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No bank with this id")
    })
    public ResponseEntity<ApiResponse<Void>> deleteBank(@PathVariable Long id) {
        questionBankService.deactivateBank(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Remove a question from a bank")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Question removed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No such question in this bank")
    })
    public ResponseEntity<ApiResponse<Void>> removeQuestion(
            @PathVariable Long id, @PathVariable Long questionId) {
        questionBankService.removeQuestion(id, questionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Add a question to a bank")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Question added"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "options/correctAnswerIndices shape is invalid for the question type"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No bank with this id")
    })
    public ResponseEntity<ApiResponse<QuestionBankItemResponse>> addQuestion(
            @PathVariable Long id, @Valid @RequestBody AddBankQuestionRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(questionBankService.addQuestion(id, request, callerUserId)));
    }

    @GetMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "List the questions in a bank (correct-answer keys are never returned)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Question list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No bank with this id")
    })
    public ResponseEntity<ApiResponse<PageResponse<QuestionBankItemResponse>>> listQuestions(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(questionBankService.listQuestions(id, pageable)));
    }
}
