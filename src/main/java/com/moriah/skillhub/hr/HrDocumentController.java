package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.common.security.CurrentUserUuid;
import com.moriah.skillhub.hr.dto.HrDocumentResponse;
import com.moriah.skillhub.hr.dto.VerifyHrDocumentRequest;
import com.moriah.skillhub.hr.entity.HrDocumentStatus;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/hr/documents")
@RequiredArgsConstructor
@Tag(name = "HR")
public class HrDocumentController {

    private final HrDocumentService hrDocumentService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List HR documents — an employee sees only their own; HR_MANAGER / ADMIN "
            + "see all, optionally filtered by status / userUuid / documentType. Each row carries "
            + "a short-lived presigned downloadUrl.")
    public ResponseEntity<ApiResponse<PageResponse<HrDocumentResponse>>> list(
            @RequestParam(required = false) HrDocumentStatus status,
            @RequestParam(required = false) String userUuid,
            @RequestParam(required = false) String documentType,
            @CurrentUser Long callerUserId,
            @CurrentUserUuid String callerUuid,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                hrDocumentService.list(userUuid, status, documentType, callerUserId, callerUuid, pageable)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<HrDocumentResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String documentType,
            @CurrentUser Long callerUserId) {

        HrDocumentResponse response = hrDocumentService.upload(callerUserId, documentType, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<HrDocumentResponse>> verify(
            @PathVariable Long id,
            @Valid @RequestBody VerifyHrDocumentRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(hrDocumentService.verify(id, request, callerUserId)));
    }
}
