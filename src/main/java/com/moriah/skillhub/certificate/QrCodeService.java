package com.moriah.skillhub.certificate;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * library-docs.md "OpenPDF + ZXing" — a QR code renders to a PNG byte array, ready for {@code
 * com.lowagie.text.Image.getInstance(byte[])} to embed into a certificate PDF. This class knows
 * nothing about certificates or verification codes — {@code CertificateService} builds the actual
 * verify URL (via {@link CertificateProperties}) and passes it in as plain content, per that same
 * doc's rule: "the QR encodes a URL to the public verification endpoint, never the certificate
 * data itself" — enforced by this class simply never accepting anything but a {@code String} to
 * encode, with no certificate-shaped parameter to misuse.
 */
@Service
@Slf4j
public class QrCodeService {

    /** {@code ErrorCorrectionLevel.M} (~15% recovery) — a reasonable default for a code that will
     * be printed on paper and scanned by a phone camera, not a lossless digital channel; higher
     * than {@code L} without the density cost of {@code Q}/{@code H} for the short URL this
     * feature ever encodes. {@code MARGIN} 1 keeps the printed quiet zone small since the PDF page
     * around it already provides whitespace. */
    private static final Map<EncodeHintType, Object> HINTS = Map.of(
            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN, 1);

    public byte[] png(String content, int sizePx) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, HINTS);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", out);
                return out.toByteArray();
            }
        } catch (WriterException | IOException e) {
            log.error("[certificate/qr] QR PNG rendering failed", e);
            throw new IllegalStateException("QR code rendering failed", e);
        }
    }
}
