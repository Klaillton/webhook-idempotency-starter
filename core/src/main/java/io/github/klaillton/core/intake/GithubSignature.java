package io.github.klaillton.core.intake;

import java.time.Instant;
import java.util.List;

/** GitHub {@code X-Hub-Signature-256}: {@code sha256=} plus the hex HMAC of the raw body. */
public final class GithubSignature {

    private GithubSignature() {}

    public static boolean verifies(byte[] body, String header, List<SigningSecret> secrets, Instant now) {
        if (header == null || !header.startsWith("sha256=")) {
            return false;
        }
        var provided = HexBytes.decode(header.substring("sha256=".length()));
        if (provided.isEmpty()) {
            return false;
        }
        var matched = false;
        for (var secret : secrets) {
            if (secret.activeAt(now)) {
                matched |= HmacSha256.matches(secret.key(), body, provided.get());
            }
        }
        return matched;
    }
}
