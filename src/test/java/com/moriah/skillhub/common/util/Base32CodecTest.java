package com.moriah.skillhub.common.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RFC 4648 §10 publishes these exact input/output pairs as its Base32 test vectors. */
class Base32CodecTest {

    @Test
    void encode_matchesRfc4648TestVectors() {
        // RFC 4648 §10's official vectors, with the '=' padding stripped — this implementation
        // deliberately never pads (see Base32Codec's Javadoc / TOTP provisioning URIs don't use
        // padded secrets), so the unpadded prefix is what must match.
        assertThat(Base32Codec.encode(bytes(""))).isEqualTo("");
        assertThat(Base32Codec.encode(bytes("f"))).isEqualTo("MY");
        assertThat(Base32Codec.encode(bytes("fo"))).isEqualTo("MZXQ");
        assertThat(Base32Codec.encode(bytes("foo"))).isEqualTo("MZXW6");
        assertThat(Base32Codec.encode(bytes("foob"))).isEqualTo("MZXW6YQ");
        assertThat(Base32Codec.encode(bytes("fooba"))).isEqualTo("MZXW6YTB");
        assertThat(Base32Codec.encode(bytes("foobar"))).isEqualTo("MZXW6YTBOI");
    }

    @Test
    void decode_isTheInverseOfEncode_forRandomLikeBinaryData() {
        byte[] original = {0, 1, 2, 3, 4, 5, 6, 7, (byte) 255, (byte) 128, 64, 32, 16, 8, 4, 2, 1, 0, (byte) 200, 99};
        String encoded = Base32Codec.encode(original);
        assertThat(Base32Codec.decode(encoded)).isEqualTo(original);
    }

    @Test
    void decode_isCaseInsensitiveAndIgnoresPadding() {
        assertThat(Base32Codec.decode("mzxw6ytb")).isEqualTo(bytes("fooba"));
        assertThat(Base32Codec.decode("MZXW6YQ=")).isEqualTo(bytes("foob"));
    }

    @Test
    void decode_rejectsInvalidCharacters() {
        assertThatThrownBy(() -> Base32Codec.decode("1nvalid!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }
}
