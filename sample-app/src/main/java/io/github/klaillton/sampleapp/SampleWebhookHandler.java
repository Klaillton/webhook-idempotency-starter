package io.github.klaillton.sampleapp;

import io.github.klaillton.core.intake.WebhookEvent;
import io.github.klaillton.core.intake.WebhookHandler;
import io.github.klaillton.core.intake.WebhookResult;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Sample handler. The counter lets the Cucumber steps see a replay. */
@Component
public final class SampleWebhookHandler implements WebhookHandler {

    public static final byte[] ACCEPTED = "{\"accepted\":true}".getBytes(StandardCharsets.UTF_8);

    private final AtomicInteger deliveriesHandled = new AtomicInteger();

    @Override
    public WebhookResult handle(WebhookEvent event) {
        deliveriesHandled.incrementAndGet();
        return new WebhookResult(ACCEPTED);
    }

    public int deliveriesHandled() {
        return deliveriesHandled.get();
    }

    public void resetDeliveriesHandled() {
        deliveriesHandled.set(0);
    }
}
