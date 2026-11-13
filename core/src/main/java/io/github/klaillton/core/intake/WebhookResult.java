package io.github.klaillton.core.intake;

/** Opaque handler bytes. Replay returns this array unchanged. */
public record WebhookResult(byte[] body) {

    public WebhookResult {
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
