package com.moriah.skillhub.project;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.project.dto.ChallengeResponse;
import com.moriah.skillhub.project.entity.ProjectDifficulty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** build-plan.md feature 15: "Bug-fix challenges with broken code, expected behaviour, test
 * script." Split from {@link ProjectController} matching architecture.md's own package diagram
 * ({@code ProjectController, ChallengeController}). */
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
}
