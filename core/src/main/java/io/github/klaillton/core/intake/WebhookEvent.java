package io.github.klaillton.core.intake;

import java.util.Arrays;
import java.util.Objects;

/** The delivery handed to the application handler. {@code body} is the raw bytes. */
public record WebhookEvent(String source, String key, byte[] body, String fingerprint) {

    public WebhookEvent {
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WebhookEvent(
                String otherSource, String otherKey, byte[] otherBody, String otherFingerprint))) {
            return false;
        }
        return source.equals(otherSource)
                && key.equals(otherKey)
                && Arrays.equals(body, otherBody)
                && fingerprint.equals(otherFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, key, Arrays.hashCode(body), fingerprint);
    }

    @Override
    public String toString() {
        return "WebhookEvent[source="
                + source
                + ", key="
                + key
                + ", bodyLength="
                + body.length
                + ", fingerprint="
                + fingerprint
                + "]";
    }
}
