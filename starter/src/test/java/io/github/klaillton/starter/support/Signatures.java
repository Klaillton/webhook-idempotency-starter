package io.github.klaillton.starter.support;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class Signatures {

    private Signatures() {}

    public static String github(String secret, byte[] body) {
        return "sha256=" + hex(hmac(secret, body));
    }

    public static String stripe(String secret, byte[] body, long timestamp) {
        var prefix = (timestamp + ".").getBytes(StandardCharsets.US_ASCII);
        var payload = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(body, 0, payload, prefix.length, body.length);
        return "t=" + timestamp + ",v1=" + hex(hmac(secret, payload));
    }

    private static byte[] hmac(String secret, byte[] payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    private static String hex(byte[] raw) {
        var builder = new StringBuilder(raw.length * 2);
        for (byte value : raw) {
            builder.append(Character.forDigit((value >> 4) & 0xf, 16));
            builder.append(Character.forDigit(value & 0xf, 16));
        }
        return builder.toString();
    }
}
