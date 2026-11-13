package io.github.klaillton.core.intake;

import java.util.Arrays;

/** Opaque handler bytes. Replay returns this array unchanged. */
public record WebhookResult(byte[] body) {

    public WebhookResult {
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WebhookResult(byte[] otherBody) && Arrays.equals(body, otherBody);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(body);
    }

    @Override
    public String toString() {
        return "WebhookResult[bodyLength=" + body.length + "]";
    }
}
