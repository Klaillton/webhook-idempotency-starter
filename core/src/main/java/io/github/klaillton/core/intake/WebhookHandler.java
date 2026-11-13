package io.github.klaillton.core.intake;

/** Application effect. It runs inside the same unit of work as the idempotency row. */
public interface WebhookHandler {

    WebhookResult handle(WebhookEvent event);
}
