package com.moriah.skillhub.user;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.user.dto.PortfolioResponse;
import com.moriah.skillhub.user.dto.ResumeDownloadResponse;
import com.moriah.skillhub.user.dto.UpdateProfileRequest;
import com.moriah.skillhub.user.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** architecture.md's {@code user/} package diagram lists one controller for this whole feature,
 * spanning both {@code /api/v1/users/**} and the public {@code /api/v1/portfolio/**} — per-method
 * paths instead of a class-level {@code @RequestMapping}, same reason {@code SecurityConfig}
 * treats those two prefixes differently (only the latter is public). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    private final ProfileService profileService;
    private final ResumeService resumeService;

    @GetMapping("/api/v1/users/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "The caller's own profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(@CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getMe(callerUserId)));
    }

    @PutMapping("/api/v1/users/me/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update the caller's profile fields")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(profileService.updateProfile(callerUserId, request)));
    }

    @PostMapping("/api/v1/users/me/resume")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload (or replace) the caller's resume PDF")
    public ResponseEntity<ApiResponse<UserProfileResponse>> uploadResume(
            @RequestParam("file") MultipartFile file,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(resumeService.upload(callerUserId, file)));
    }

    @GetMapping("/api/v1/users/me/resume")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "A presigned URL for downloading the caller's resume")
    public ResponseEntity<ApiResponse<ResumeDownloadResponse>> resumeDownloadUrl(@CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(resumeService.getDownloadUrl(callerUserId)));
    }

    /** Public at the filter level (architecture.md "GET /portfolio/{slug} public") — no {@code
     * @PreAuthorize}, matching {@code PlanController}'s stated exception for explicitly public
     * endpoints. */
    @GetMapping("/api/v1/portfolio/{slug}")
    @Operation(summary = "A student's public portfolio")
    public ResponseEntity<ApiResponse<PortfolioResponse>> portfolio(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(userService.getPortfolio(slug)));
    }
}
