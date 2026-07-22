package com.ecommerce.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Verificacion en dos pasos por TOTP (RFC 6238), compatible con Google Authenticator y Authy.
 *
 * <p>Se implementa aqui en lugar de anadir una dependencia externa: el algoritmo son treinta lineas
 * y evita arrastrar una libreria mas al arranque.
 */
@Service
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;      // 160 bits, lo recomendado por la RFC
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    /** Tolerancia de un paso hacia atras y hacia delante, por desfase de reloj. */
    private static final int ALLOWED_DRIFT_STEPS = 1;

    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** URI {@code otpauth://} que el frontend convierte en codigo QR. */
    public String buildOtpAuthUri(String secret, String accountEmail, String issuer) {
        String label = URLEncoder.encode(issuer + ":" + accountEmail, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    public boolean verify(String secret, String code) {
        if (secret == null || code == null) {
            return false;
        }
        String normalized = code.trim().replace(" ", "");
        if (normalized.length() != DIGITS || !normalized.chars().allMatch(Character::isDigit)) {
            return false;
        }
        long currentStep = System.currentTimeMillis() / 1000L / PERIOD_SECONDS;
        byte[] key = base32Decode(secret);
        for (int drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            if (constantTimeEquals(generateCode(key, currentStep + drift), normalized)) {
                return true;
            }
        }
        return false;
    }

    private String generateCode(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());

            // Truncamiento dinamico definido por la RFC 4226.
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return ("%0" + DIGITS + "d").formatted(binary % (int) Math.pow(10, DIGITS));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el codigo TOTP", e);
        }
    }

    /** Comparacion sin cortocircuito, para no filtrar informacion por tiempo de respuesta. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    static byte[] base32Decode(String encoded) {
        String clean = encoded.trim().replace("=", "").toUpperCase();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Secreto TOTP invalido");
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
