package com.moriah.skillhub.certificate;

import com.moriah.skillhub.certificate.dto.PublicVerificationResponse;
import com.moriah.skillhub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/certificates/verify/{code}} — public, unauthenticated, no {@code
 * @PreAuthorize} at all (this path is in {@code SecurityConfig}'s public-path list, same as
 * {@code /api/v1/portfolio/**}). Deliberately its own controller/class, separate from {@link
 * CertificateController}'s authenticated endpoints — see that controller's own Javadoc. */
@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
@Tag(name = "Certificates")
public class VerificationController {

    private final CertificateService certificateService;

    @GetMapping("/verify/{code}")
    @Operation(summary = "Public certificate verification by code — never accepts a certificate id")
    public ResponseEntity<ApiResponse<PublicVerificationResponse>> verify(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(certificateService.verify(code)));
    }
}
