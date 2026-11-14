package io.github.klaillton.starter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.klaillton.core.intake.BodyLimit;
import io.github.klaillton.core.intake.Fingerprint;
import io.github.klaillton.starter.support.IntakeFixtures;
import io.github.klaillton.starter.support.IntakeFixtures.EffectHandler;
import io.github.klaillton.starter.support.IntakeTestApplication;
import io.github.klaillton.starter.support.Signatures;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = IntakeTestApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class WebhookHttpPostgresTest {

    private static final byte[] PUSH = "{\"event\":\"push\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER = "{\"event\":\"other\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] STRIPE_EVENT = "{\"id\":\"evt_1001\"}".getBytes(StandardCharsets.UTF_8);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void secrets(DynamicPropertyRegistry registry) {
        registry.add("webhook.idempotency.lease", () -> "2m");
        registry.add("webhook.idempotency.timestamp-tolerance", () -> "5m");
        registry.add("webhook.idempotency.sources.github.secrets[0].key", () -> IntakeFixtures.EXPIRED_SECRET);
        registry.add("webhook.idempotency.sources.github.secrets[0].expires-at", () -> "2020-01-01T00:00:00Z");
        registry.add("webhook.idempotency.sources.github.secrets[1].key", () -> IntakeFixtures.GITHUB_SECRET);
        registry.add("webhook.idempotency.sources.stripe.secrets[0].key", () -> IntakeFixtures.STRIPE_SECRET);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EffectHandler handler;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void clean() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS handler_effect (id text PRIMARY KEY)");
        jdbc.update("DELETE FROM handler_effect");
        jdbc.update("DELETE FROM idempotency_record");
        handler.reset();
    }

    @Test
    void shouldReturnHandlerResultWhenSignatureIsValid() throws Exception {
        var response = postGithub("delivery-1001", PUSH, IntakeFixtures.GITHUB_SECRET);

        assertEquals(200, response.statusCode());
        assertArrayEquals(IntakeFixtures.ACCEPTED, response.body());
        assertEquals(1, handler.calls());
        assertEquals("COMPLETED", statusOf("delivery-1001"));
        assertArrayEquals(IntakeFixtures.ACCEPTED, storedBody("delivery-1001"));
    }

    @Test
    void shouldReplayStoredResultWhenDeliveryIsRepeated() throws Exception {
        assertEquals(200, postGithub("delivery-1001", PUSH, IntakeFixtures.GITHUB_SECRET).statusCode());

        var replay = postGithub("delivery-1001", PUSH, IntakeFixtures.GITHUB_SECRET);

        assertEquals(200, replay.statusCode());
        assertArrayEquals(IntakeFixtures.ACCEPTED, replay.body());
        assertEquals(1, handler.calls());
        assertEquals(1, effectCount());
    }

    @Test
    void shouldRejectWhenSignatureDoesNotMatch() throws Exception {
        var response = postGithub("delivery-1001", PUSH, "other-secret");

        assertEquals(401, response.statusCode());
        assertEquals(0, countRows());
        assertEquals(0, handler.calls());
    }

    @Test
    void shouldRejectWhenGithubSecretIsExpired() throws Exception {
        var response = postGithub("delivery-1001", PUSH, IntakeFixtures.EXPIRED_SECRET);

        assertEquals(401, response.statusCode());
        assertEquals(0, countRows());
    }

    @Test
    void shouldLeaveStoreUntouchedWhenKeyIsMissing() throws Exception {
        var response = post("github", null, PUSH, "X-Hub-Signature-256", github(PUSH, IntakeFixtures.GITHUB_SECRET));

        assertNotEquals(200, response.statusCode());
        assertNotEquals(400, response.statusCode());
        assertEquals(0, countRows());
    }

    @Test
    void shouldRejectWhenFingerprintDiffers() throws Exception {
        assertEquals(200, postGithub("delivery-1001", PUSH, IntakeFixtures.GITHUB_SECRET).statusCode());
        var stored = storedBody("delivery-1001");

        var response = postGithub("delivery-1001", OTHER, IntakeFixtures.GITHUB_SECRET);

        assertEquals(422, response.statusCode());
        assertArrayEquals(stored, storedBody("delivery-1001"));
        assertEquals(1, handler.calls());
    }

    @Test
    void shouldReturnRetryAfterWhenLeaseIsValid() throws Exception {
        seed("delivery-1001", IntakeFixtures.FIXED.plusMillis(90_500), Fingerprint.sha256Hex(PUSH));

        var response = postGithub("delivery-1001", PUSH, IntakeFixtures.GITHUB_SECRET);

        assertEquals(409, response.statusCode());
        assertEquals("91", response.headers().firstValue("retry-after").orElseThrow());
        assertEquals(0, handler.calls());
        assertEquals("IN_PROGRESS", statusOf("delivery-1001"));
    }

    @Test
    void shouldRejectWhenBodyExceedsLimit() throws Exception {
        var body = new byte[BodyLimit.DEFAULT_BYTES + 1];
        var response = post("github", "delivery-1001", body, "X-Hub-Signature-256", github(body, IntakeFixtures.GITHUB_SECRET));

        assertEquals(413, response.statusCode());
        assertEquals(0, countRows());
    }

    @Test
    void shouldTakeOverWhenLeaseIsExpired() throws Exception {
        seed("delivery-1001", IntakeFixtures.FIXED.minusSeconds(30), "0".repeat(64));

        var response = postGithub("delivery-1001", PUSH, IntakeFixtures.GITHUB_SECRET);

        assertEquals(200, response.statusCode());
        assertEquals(1, handler.calls());
        assertEquals("COMPLETED", statusOf("delivery-1001"));
        assertEquals(Fingerprint.sha256Hex(PUSH), fingerprintOf("delivery-1001"));
    }

    @Test
    void shouldRemoveReservationWhenHandlerFails() throws Exception {
        handler.failNext();

        var response = postGithub("delivery-1001", PUSH, IntakeFixtures.GITHUB_SECRET);

        assertFalse(response.statusCode() >= 200 && response.statusCode() < 300);
        assertEquals(0, countRows());
        assertEquals(0, effectCount());
    }

    @Test
    void shouldRunHandlerOnceWhenDeliveriesOverlap() throws Exception {
        handler.blockUntilReleased();
        var first = CompletableFuture.supplyAsync(() -> postGithubUnchecked("delivery-overlap", PUSH));
        try {
            assertTrue(handler.awaitEntered());
            var second = CompletableFuture.supplyAsync(() -> postGithubUnchecked("delivery-overlap", PUSH));
            Thread.sleep(300);
            assertEquals(1, handler.calls());
            handler.release();

            var left = first.get(20, TimeUnit.SECONDS);
            var right = second.get(20, TimeUnit.SECONDS);

            assertEquals(200, left.statusCode());
            assertEquals(200, right.statusCode());
            assertArrayEquals(IntakeFixtures.ACCEPTED, left.body());
            assertArrayEquals(right.body(), left.body());
            assertEquals(1, handler.calls());
            assertEquals(1, effectCount());
        } finally {
            handler.release();
        }
    }

    @Test
    void shouldAcceptWhenStripeSignatureMatches() throws Exception {
        var timestamp = IntakeFixtures.FIXED.getEpochSecond();
        var signature = Signatures.stripe(IntakeFixtures.STRIPE_SECRET, STRIPE_EVENT, timestamp);

        var response = post("stripe", null, STRIPE_EVENT, "Stripe-Signature", signature);

        assertEquals(200, response.statusCode());
        assertArrayEquals(IntakeFixtures.ACCEPTED, response.body());
        assertEquals("COMPLETED", statusOf("evt_1001"));
    }

    @Test
    void shouldRejectWhenSourceHasNoSignatureScheme() throws Exception {
        var response = post("custom", "delivery-1001", PUSH, "Idempotency-Key", "delivery-1001");

        assertEquals(401, response.statusCode());
        assertEquals(0, countRows());
    }

    private HttpResponse<byte[]> postGithubUnchecked(String delivery, byte[] body) {
        try {
            return postGithub(delivery, body, IntakeFixtures.GITHUB_SECRET);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private HttpResponse<byte[]> postGithub(String delivery, byte[] body, String secret) throws Exception {
        return post("github", delivery, body, "X-Hub-Signature-256", github(body, secret));
    }

    private static String github(byte[] body, String secret) {
        return Signatures.github(secret, body);
    }

    private HttpResponse<byte[]> post(String source, String delivery, byte[] body, String headerName, String headerValue)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/webhooks/" + source))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (delivery != null) {
            builder.header("X-GitHub-Delivery", delivery);
        }
        if (headerValue != null) {
            builder.header(headerName, headerValue);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private void seed(String key, Instant lockedUntil, String fingerprint) {
        jdbc.update(
                """
                INSERT INTO idempotency_record
                    (source, key, fingerprint, status, locked_until, created_at)
                VALUES (?, ?, ?, 'IN_PROGRESS', ?, CURRENT_TIMESTAMP)
                """,
                "github",
                key,
                fingerprint,
                lockedUntil.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
    }

    private int countRows() {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record", Integer.class);
        return count == null ? 0 : count;
    }

    private int effectCount() {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM handler_effect", Integer.class);
        return count == null ? 0 : count;
    }

    private String statusOf(String key) {
        return jdbc.queryForObject(
                "SELECT status FROM idempotency_record WHERE key = ?", String.class, key);
    }

    private String fingerprintOf(String key) {
        return jdbc.queryForObject(
                "SELECT fingerprint FROM idempotency_record WHERE key = ?", String.class, key);
    }

    private byte[] storedBody(String key) {
        return jdbc.queryForObject(
                "SELECT response_body FROM idempotency_record WHERE key = ?",
                (rs, row) -> rs.getBytes("response_body"),
                key);
    }
}
