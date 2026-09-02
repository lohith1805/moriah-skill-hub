package com.moriah.skillhub.project;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.project.dto.ChallengeResponse;
import com.moriah.skillhub.project.dto.UpdateChallengeRequest;
import com.moriah.skillhub.project.entity.ProjectDifficulty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** build-plan.md feature 15: "Bug-fix challenges with broken code, expected behaviour, test
 * script." Split from {@link ProjectController} matching architecture.md's own package diagram
 * ({@code ProjectController, ChallengeController}). Feature 15 shipped only the create endpoint;
 * gap B1.15 adds list / detail / update / delete for the FE's challenge-management screen. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Bug Challenges")
public class ChallengeController {

    private final ProjectService projectService;

    @PostMapping("/api/v1/projects/{id}/challenges")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "Attach a bug-fix challenge — brokenCode required, testScript optional")
    public ResponseEntity<ApiResponse<ChallengeResponse>> addChallenge(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam MultipartFile brokenCode,
            @RequestParam(required = false) MultipartFile testScript,
            @RequestParam String expectedBehaviour,
            @RequestParam(required = false) ProjectDifficulty difficulty,
            @CurrentUser Long callerUserId) {

        ChallengeResponse response = projectService.addChallenge(
                callerUserId, id, title, brokenCode, testScript, expectedBehaviour, difficulty);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/projects/{id}/challenges")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "List every bug-fix challenge on a project")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Challenge list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No project with this id")
    })
    public ResponseEntity<ApiResponse<List<ChallengeResponse>>> listChallenges(
            @PathVariable Long id, @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(projectService.listChallenges(callerUserId, id)));
    }

    @GetMapping("/api/v1/challenges/{challengeId}")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "One bug-fix challenge by id")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Challenge"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No challenge with this id")
    })
    public ResponseEntity<ApiResponse<ChallengeResponse>> getChallenge(
            @PathVariable Long challengeId, @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(projectService.getChallenge(callerUserId, challengeId)));
    }

    @PutMapping("/api/v1/challenges/{challengeId}")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "Edit a challenge's title / expected behaviour / difficulty — creator or ADMIN only")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Challenge updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the creator and not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No challenge with this id"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "The owning project version is archived")
    })
    public ResponseEntity<ApiResponse<ChallengeResponse>> updateChallenge(
            @PathVariable Long challengeId, @Valid @RequestBody UpdateChallengeRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(projectService.updateChallenge(
                callerUserId, challengeId, request.title(), request.expectedBehaviour(), request.difficulty())));
    }

    @DeleteMapping("/api/v1/challenges/{challengeId}")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "Delete a bug-fix challenge — creator or ADMIN only")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Challenge deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the creator and not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No challenge with this id")
    })
    public ResponseEntity<ApiResponse<Void>> deleteChallenge(
            @PathVariable Long challengeId, @CurrentUser Long callerUserId) {
        projectService.deleteChallenge(callerUserId, challengeId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
