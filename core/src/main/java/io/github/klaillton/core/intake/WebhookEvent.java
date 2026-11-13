package io.github.klaillton.core.intake;

/** The delivery handed to the application handler. {@code body} is the raw bytes. */
public record WebhookEvent(String source, String key, byte[] body, String fingerprint) {

    public WebhookEvent {
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
