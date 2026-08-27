package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.hr.dto.HrDocumentResponse;
import com.moriah.skillhub.hr.dto.VerifyHrDocumentRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
