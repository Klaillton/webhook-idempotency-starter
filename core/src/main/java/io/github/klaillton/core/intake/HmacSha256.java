package io.github.klaillton.core.intake;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class HmacSha256 {

    private HmacSha256() {}

    static boolean matches(byte[] key, byte[] payload, byte[] provided) {
        if (provided.length != 32) {
            return false;
        }
        return MessageDigest.isEqual(sign(key, payload), provided);
    }

    private static byte[] sign(byte[] key, byte[] payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }
}
