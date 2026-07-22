package com.ecommerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * La verificacion en dos pasos se implementa en casa, asi que estas pruebas comprueban que sigue
 * siendo compatible con la RFC 6238 y, por tanto, con Google Authenticator o Authy.
 */
class TotpServiceTest {

    private final TotpService service = new TotpService();

    @Test
    void base32RoundTripPreservesBytes() {
        byte[] original = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

        String encoded = TotpService.base32Encode(original);
        byte[] decoded = TotpService.base32Decode(encoded);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void base32EncodesTheRfc4648TestVector() {
        // Vector de prueba de la RFC 4648: "foobar" en base32 es MZXW6YTBOI.
        assertThat(TotpService.base32Encode("foobar".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("MZXW6YTBOI");
    }

    @Test
    void generatedSecretIsUsableBase32() {
        String secret = service.generateSecret();

        assertThat(secret).matches("[A-Z2-7]+");
        // 20 bytes son 160 bits, que en base32 ocupan 32 caracteres.
        assertThat(secret).hasSize(32);
        assertThat(TotpService.base32Decode(secret)).hasSize(20);
    }

    @Test
    void acceptsACodeCalculatedIndependently() {
        String secret = service.generateSecret();

        assertThat(service.verify(secret, independentTotp(secret, 0))).isTrue();
    }

    @Test
    void toleratesOneStepOfClockDrift() {
        String secret = service.generateSecret();

        assertThat(service.verify(secret, independentTotp(secret, -1))).isTrue();
        assertThat(service.verify(secret, independentTotp(secret, 1))).isTrue();
    }

    @Test
    void rejectsCodesTooFarFromTheCurrentStep() {
        String secret = service.generateSecret();

        assertThat(service.verify(secret, independentTotp(secret, 5))).isFalse();
    }

    @Test
    void rejectsMalformedInput() {
        String secret = service.generateSecret();

        assertThat(service.verify(secret, "12345")).isFalse();     // muy corto
        assertThat(service.verify(secret, "1234567")).isFalse();   // muy largo
        assertThat(service.verify(secret, "abcdef")).isFalse();    // no numerico
        assertThat(service.verify(secret, null)).isFalse();
        assertThat(service.verify(null, "123456")).isFalse();
    }

    @Test
    void rejectsAnInvalidSecret() {
        assertThatThrownBy(() -> TotpService.base32Decode("no-es-base32!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otpAuthUriCarriesTheSecretAndIssuer() {
        String uri = service.buildOtpAuthUri("JBSWY3DPEHPK3PXP", "ana@empresa.local", "E-Commerce");

        assertThat(uri)
                .startsWith("otpauth://totp/")
                .contains("secret=JBSWY3DPEHPK3PXP")
                .contains("issuer=E-Commerce")
                .contains("digits=6")
                .contains("period=30");
    }

    /**
     * Calculo de TOTP escrito aparte, sin reutilizar el codigo de produccion, para que la prueba
     * detecte un error en la implementacion en lugar de repetirlo.
     */
    private String independentTotp(String base32Secret, int stepOffset) {
        try {
            byte[] key = TotpService.base32Decode(base32Secret);
            long step = System.currentTimeMillis() / 1000L / 30L + stepOffset;

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
