package io.github.klaillton.starter.support;

import io.github.klaillton.core.intake.WebhookEvent;
import io.github.klaillton.core.intake.WebhookHandler;
import io.github.klaillton.core.intake.WebhookResult;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@TestConfiguration
public class IntakeFixtures {

    public static final Instant FIXED = Instant.parse("2026-06-01T12:00:00Z");
    public static final byte[] ACCEPTED = "{\"accepted\":true}".getBytes(StandardCharsets.UTF_8);
    public static final String GITHUB_SECRET = "example-github-secret";
    public static final String EXPIRED_SECRET = "expired-example";
    public static final String STRIPE_SECRET = "example-stripe-secret";

    @Bean
    @Primary
    Clock clock() {
        return Clock.fixed(FIXED, ZoneOffset.UTC);
    }

    @Bean
    EffectHandler effectHandler(JdbcTemplate jdbcTemplate) {
        return new EffectHandler(jdbcTemplate);
    }

    public static final class EffectHandler implements WebhookHandler {

        private final JdbcTemplate jdbc;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicBoolean fail = new AtomicBoolean();
        private final AtomicBoolean block = new AtomicBoolean();
        private volatile CountDownLatch entered = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);

        EffectHandler(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public WebhookResult handle(WebhookEvent event) {
            calls.incrementAndGet();
            jdbc.update("INSERT INTO handler_effect (id) VALUES (?)", event.key());
            if (fail.get()) {
                throw new IllegalStateException("handler failed");
            }
            if (block.get()) {
                entered.countDown();
                awaitRelease();
            }
            return new WebhookResult(ACCEPTED);
        }

        public int calls() {
            return calls.get();
        }

        public void failNext() {
            fail.set(true);
        }

        public void blockUntilReleased() {
            block.set(true);
        }

        public void release() {
            release.countDown();
        }

        public boolean awaitEntered() throws InterruptedException {
            return entered.await(15, TimeUnit.SECONDS);
        }

        public void reset() {
            calls.set(0);
            fail.set(false);
            block.set(false);
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        private void awaitRelease() {
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
}
