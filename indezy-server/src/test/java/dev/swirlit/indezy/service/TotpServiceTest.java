package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    /** The 20-byte ASCII seed used by the RFC 6238 SHA-1 test vectors. */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.UTF_8);

    @Test
    void generateCodeMatchesRfc6238Sha1Vectors() {
        // Values are the RFC 6238 Appendix B SHA-1 TOTPs reduced to six digits.
        assertThat(totpService.generateCode(RFC_KEY, 1L)).isEqualTo("287082");
        assertThat(totpService.generateCode(RFC_KEY, 37037036L)).isEqualTo("081804");
        assertThat(totpService.generateCode(RFC_KEY, 41152263L)).isEqualTo("005924");
        assertThat(totpService.generateCode(RFC_KEY, 66666666L)).isEqualTo("279037");
    }

    @Test
    void generateSecretProducesDecodableBase32() {
        String secret = totpService.generateSecret();
        assertThat(secret).isNotBlank().matches("[A-Z2-7]+");
    }

    @Test
    void validateCodeAcceptsAFreshlyGeneratedCode() {
        String secret = totpService.generateSecret();
        String code = totpService.generateCurrentCode(secret);

        assertThat(totpService.validateCode(secret, code)).isTrue();
        assertThat(totpService.validateCode(secret, "000000".equals(code) ? "111111" : "000000")).isFalse();
    }

    @Test
    void buildProvisioningUriContainsSecretAndIssuer() {
        String uri = totpService.buildProvisioningUri("ABC234", "john@example.com", "Indezy");
        assertThat(uri)
            .startsWith("otpauth://totp/Indezy:")
            .contains("secret=ABC234")
            .contains("issuer=Indezy")
            .contains("digits=6")
            .contains("period=30");
    }
}
