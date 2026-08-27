package com.moriah.skillhub.common.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Package-private test — {@link FileSignatures} itself is package-private by design (only
 * {@link S3StorageService} needs it), so this lives alongside it rather than in the root test
 * package every {@code IT} class uses. */
class FileSignaturesTest {

    @Test
    void isSupported_recognizesEveryDocumentedType() {
        assertThat(FileSignatures.isSupported("application/pdf")).isTrue();
        assertThat(FileSignatures.isSupported("image/jpeg")).isTrue();
        assertThat(FileSignatures.isSupported("image/png")).isTrue();
        assertThat(FileSignatures.isSupported("application/x-msdownload")).isFalse();
    }

    @Test
    void matches_pdfSignature_acceptsRealPdfBytes() {
        byte[] content = "%PDF-1.4\n%rest of a real pdf".getBytes(StandardCharsets.UTF_8);
        assertThat(FileSignatures.matches("application/pdf", content)).isTrue();
    }

    @Test
    void matches_pdfContentType_rejectsMismatchedBytes() {
        byte[] content = "just some text, not a pdf at all".getBytes(StandardCharsets.UTF_8);
        assertThat(FileSignatures.matches("application/pdf", content)).isFalse();
    }

    @Test
    void matches_jpegSignature_acceptsRealJpegBytes() {
        byte[] content = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        assertThat(FileSignatures.matches("image/jpeg", content)).isTrue();
    }

    @Test
    void matches_pngSignature_acceptsRealPngBytes() {
        byte[] content = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        assertThat(FileSignatures.matches("image/png", content)).isTrue();
    }

    @Test
    void matches_contentShorterThanSignature_returnsFalseRatherThanThrowing() {
        assertThat(FileSignatures.matches("application/pdf", new byte[]{0x25})).isFalse();
    }

    @Test
    void matches_unrecognizedContentType_returnsFalse() {
        byte[] content = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
        assertThat(FileSignatures.matches("application/octet-stream", content)).isFalse();
    }
}
