package io.github.klaillton.sampleapp;

import io.github.klaillton.core.intake.ResponseRetention;
import io.github.klaillton.core.intake.WebhookEvent;
import io.github.klaillton.core.intake.WebhookHandler;
import io.github.klaillton.core.intake.WebhookResult;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Sample handler. The counter, the one-shot failure, the hold, and the oversized result let the
 * Cucumber steps see a replay, a rolled-back delivery, two callers that overlap, and a body that
 * is not stored.
 */
@Component
public final class SampleWebhookHandler implements WebhookHandler {

    public static final byte[] ACCEPTED = "{\"accepted\":true}".getBytes(StandardCharsets.UTF_8);

    private final AtomicInteger deliveriesHandled = new AtomicInteger();
    private final AtomicBoolean failNext = new AtomicBoolean();
    private final AtomicBoolean holdNext = new AtomicBoolean();
    private final AtomicBoolean oversizedNext = new AtomicBoolean();
    private volatile CountDownLatch entered = new CountDownLatch(1);
    private volatile CountDownLatch release = new CountDownLatch(1);

    @Override
    public WebhookResult handle(WebhookEvent event) {
        deliveriesHandled.incrementAndGet();
        if (failNext.compareAndSet(true, false)) {
            throw new IllegalStateException("handler failed");
        }
        if (holdNext.compareAndSet(true, false)) {
            hold();
        }
        if (oversizedNext.compareAndSet(true, false)) {
            return new WebhookResult(new byte[ResponseRetention.MAX_STORED_BODY_BYTES + 1]);
        }
        return new WebhookResult(ACCEPTED);
    }

    public int deliveriesHandled() {
        return deliveriesHandled.get();
    }

    /** The next handle throws once. A later delivery of the same key can run. */
    public void failNextDelivery() {
        failNext.set(true);
    }

    /** The next handle waits until {@link #releaseHeldDelivery()}. */
    public void holdNextDelivery() {
        holdNext.set(true);
    }

    /** The next handle returns one byte past the stored-body limit. */
    public void produceOversizedResult() {
        oversizedNext.set(true);
    }

    public void releaseHeldDelivery() {
        release.countDown();
    }

    public boolean awaitHeldDelivery() throws InterruptedException {
        return entered.await(15, TimeUnit.SECONDS);
    }

    public void resetDeliveriesHandled() {
        deliveriesHandled.set(0);
        failNext.set(false);
        holdNext.set(false);
        oversizedNext.set(false);
        release.countDown();
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
    }

    private void hold() {
        entered.countDown();
        try {
            if (!release.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("handler was not released");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("handler wait interrupted", ex);
        }
    }
}
