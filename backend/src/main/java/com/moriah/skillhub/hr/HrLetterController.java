package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.common.security.CurrentUserUuid;
import com.moriah.skillhub.hr.dto.IssueLetterRequest;
import com.moriah.skillhub.hr.dto.LetterResponse;
import com.moriah.skillhub.hr.entity.LetterType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hr/letters")
@RequiredArgsConstructor
@Tag(name = "HR")
public class HrLetterController {

    private final HrLetterService hrLetterService;

    @PostMapping("/{type}")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<LetterResponse>> issue(
            @PathVariable LetterType type,
            @Valid @RequestBody IssueLetterRequest request,
            @CurrentUserUuid String callerUuid,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(hrLetterService.issue(type, request, callerUuid, callerUserId)));
    }
}
