package com.moriah.skillhub;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * build-plan.md feature 08 verify line: "Upload, retrieve via presigned URL, confirm expiry.
 * Requesting another user's resume key returns 403." Real MinIO (IntegrationTestBase), not a
 * mocked S3Client — `/architect feature 08` decision: this project already provisions MinIO in
 * docker-compose for exactly this, so upload/presign/expiry get tested for real rather than
 * mocked at the SDK boundary the way Razorpay/Stripe/OAuth2 are.
 */
class StorageServiceIT extends IntegrationTestBase {

    private static final byte[] PDF_BYTES = "%PDF-1.4 not a real pdf but starts right".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private StorageService storageService;

    @Test
    void upload_thenPresignedGetUrl_actuallyDownloadsTheSameBytes() throws Exception {
        String uuid = UUID.randomUUID().toString();
        String key = "resumes/" + uuid + "/resume.pdf";

        storageService.upload(key, PDF_BYTES, "application/pdf");
        URL url = storageService.presignedGetUrl(uuid, key, Duration.ofMinutes(15));

        // .uri(URI), not .uri(String) — the String overload treats its argument as a template and
        // re-encodes it, double-encoding the presigned URL's already-percent-encoded AWS SigV4
        // query string and corrupting the signature.
        byte[] downloaded = RestClient.create().get().uri(url.toURI()).retrieve().body(byte[].class);
        assertThat(downloaded).isEqualTo(PDF_BYTES);
    }

    @Test
    void presignedGetUrl_forAnotherUsersResumeKey_throwsForbidden() {
        String ownerUuid = UUID.randomUUID().toString();
        String callerUuid = UUID.randomUUID().toString();
        String key = "resumes/" + ownerUuid + "/resume.pdf";
        storageService.upload(key, PDF_BYTES, "application/pdf");

        assertThatThrownBy(() -> storageService.presignedGetUrl(callerUuid, key, Duration.ofMinutes(15)))
                .isInstanceOf(ForbiddenOperationException.class)
                .satisfies(e -> assertThat(((ForbiddenOperationException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_RESOURCE_OWNER));
    }

    @Test
    void presignedGetUrl_expiresAfterItsTtl() throws Exception {
        String uuid = UUID.randomUUID().toString();
        String key = "resumes/" + uuid + "/resume.pdf";
        storageService.upload(key, PDF_BYTES, "application/pdf");

        // A short-but-positive TTL, then actually wait past it — proves expiry is enforced by
        // S3/MinIO itself (the signature's own X-Amz-Expires), not just trusted client-side.
        // AWS SDK v2's presigner rejects a zero/negative signatureDuration at sign time, so this
        // can't be tested with an already-expired duration instead.
        URL url = storageService.presignedGetUrl(uuid, key, Duration.ofSeconds(1));
        Thread.sleep(2000);

        assertThatThrownBy(() -> RestClient.create().get().uri(url.toURI()).retrieve().toBodilessEntity())
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.Forbidden.class);
    }

    @Test
    void upload_unsupportedContentType_throwsUnsupportedFileType() {
        String key = "resumes/" + UUID.randomUUID() + "/resume.exe";

        assertThatThrownBy(() -> storageService.upload(key, "not a real exe".getBytes(StandardCharsets.UTF_8),
                "application/x-msdownload"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE));
    }

    @Test
    void upload_contentTypeClaimsPdfButMagicBytesDont_throwsUnsupportedFileType() {
        String key = "resumes/" + UUID.randomUUID() + "/resume.pdf";

        assertThatThrownBy(() -> storageService.upload(key, "not actually a pdf".getBytes(StandardCharsets.UTF_8),
                "application/pdf"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE));
    }

    @Test
    void upload_overMaxSize_throwsFileTooLarge() {
        String key = "resumes/" + UUID.randomUUID() + "/resume.pdf";
        byte[] oversized = new byte[(int) com.moriah.skillhub.common.util.Constants.MAX_UPLOAD_BYTES + 1];
        System.arraycopy(PDF_BYTES, 0, oversized, 0, PDF_BYTES.length);

        assertThatThrownBy(() -> storageService.upload(key, oversized, "application/pdf"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    void presignedGetUrl_forUnrecognizedKeyNamespace_throwsForbidden() {
        // certificates/ isn't recognized by OwnershipGuard yet (feature 20 territory) — "deny by
        // default for any key it doesn't recognize" applies here too.
        assertThatThrownBy(() -> storageService.presignedGetUrl(UUID.randomUUID().toString(),
                "certificates/CERT-0001.pdf", Duration.ofMinutes(15)))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
