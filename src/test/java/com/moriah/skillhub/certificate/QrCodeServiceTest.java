package com.moriah.skillhub.certificate;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** build-plan.md feature 20's own verification criterion, applied to the QR renderer directly:
 * "QR resolves publicly." Decodes the PNG {@link QrCodeService} produces with ZXing's own reader
 * (the {@code javase} module's {@link BufferedImageLuminanceSource}) and asserts the round trip
 * reproduces the exact content encoded — the only way to prove the PNG is a genuinely scannable QR
 * code, not just "some bytes that happen to be a valid PNG." */
class QrCodeServiceTest {

    private final QrCodeService qrCodeService = new QrCodeService();

    @Test
    void png_encodesUrl_decodableBackToTheSameContent() throws IOException, NotFoundException {
        String content = "http://localhost:3000/verify/ABCDEFGHJKMN";

        byte[] png = qrCodeService.png(content, 150);

        assertThat(png).isNotEmpty();
        assertThat(decode(png)).isEqualTo(content);
    }

    @Test
    void png_isARealPngImageOfTheRequestedSize() throws IOException {
        byte[] png = qrCodeService.png("http://localhost:3000/verify/XYZ123456789", 200);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(200);
        assertThat(image.getHeight()).isEqualTo(200);
    }

    private String decode(byte[] png) throws IOException, NotFoundException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Result result = new MultiFormatReader().decode(bitmap);
        return result.getText();
    }
}
