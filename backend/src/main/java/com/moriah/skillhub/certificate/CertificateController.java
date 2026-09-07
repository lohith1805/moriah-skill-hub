package com.moriah.skillhub.certificate;

import com.moriah.skillhub.certificate.dto.CertificateResponse;
import com.moriah.skillhub.certificate.dto.IssueCertificateRequest;
import com.moriah.skillhub.certificate.dto.RevokeCertificateRequest;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.common.security.CurrentUserUuid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code issue}/{@code revoke} are {@code TRAINER_PM}/{@code ADMIN}, per-batch ownership enforced
 * in {@link CertificateService} via {@code BatchService#requireOwnerOrAdmin} (same reasoning
 * {@code SprintController}'s/{@code PipController}'s own Javadoc gives). {@code me} is {@code
 * STUDENT}-only (their own certificates). The public verification endpoint lives in {@link
 * VerificationController} instead — it needs no auth at all, not even an anonymous-permitted
 * {@code @PreAuthorize}, and keeping it physically separate makes that impossible to miss in a
 * future review of this controller's role list. */
@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
@Tag(name = "Certificates")
public class CertificateController {

    private final CertificateService certificateService;

    @PostMapping("/issue")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Issue a certificate — requires GRADUATED, no open PIP, and every sprint COMPLETED")
    public ResponseEntity<ApiResponse<CertificateResponse>> issue(
            @Valid @RequestBody IssueCertificateRequest request,
            @CurrentUser Long callerUserId, @CurrentUserUuid String callerUuid) {

        CertificateResponse response = certificateService.issue(request, callerUserId, callerUuid);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "The caller's own issued certificates")
    public ResponseEntity<ApiResponse<PageResponse<CertificateResponse>>> me(
            @CurrentUser Long callerUserId, @CurrentUserUuid String callerUuid,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(certificateService.me(callerUserId, callerUuid, pageable)));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Revoke a certificate — never deletes it, still resolves publicly as invalid")
    public ResponseEntity<ApiResponse<CertificateResponse>> revoke(
            @PathVariable Long id, @Valid @RequestBody RevokeCertificateRequest request,
            @CurrentUser Long callerUserId, @CurrentUserUuid String callerUuid) {

        return ResponseEntity.ok(ApiResponse.success(certificateService.revoke(id, request, callerUserId, callerUuid)));
    }
}
