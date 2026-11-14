package io.github.klaillton.sampleapp.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import io.github.klaillton.core.intake.BodyLimit;
import io.github.klaillton.core.intake.Fingerprint;
import io.github.klaillton.sampleapp.SampleWebhookHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

@ScenarioScope
public class IntakeSteps {

    private static final byte[] PUSH = "{\"event\":\"push\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER = "{\"event\":\"other\"}".getBytes(StandardCharsets.UTF_8);
    private static final String SECRET = "example-github-secret";

    private final int port;
    private final SampleWebhookHandler handler;
    private final JdbcTemplate jdbc;
    private final ReadinessGate gate;
    private final Clock clock;
    private final HttpClient http = HttpClient.newHttpClient();

    private String source = "github";
    private String deliveryId = "delivery-1001";
    private byte[] body = PUSH;
    private boolean sign = true;
    private Integer expectedStatus;
    private HttpResponse<byte[]> response;
    private byte[] storedBefore;
    private int callsBefore;

    public IntakeSteps(
            @Value("${local.server.port}") int port,
            SampleWebhookHandler handler,
            JdbcTemplate jdbc,
            ReadinessGate gate,
            Clock clock) {
        this.port = port;
        this.handler = handler;
        this.jdbc = jdbc;
        this.gate = gate;
        this.clock = clock;
    }

    @Before
    public void reset() {
        jdbc.update("DELETE FROM idempotency_record");
        handler.resetDeliveriesHandled();
        gate.ready();
        source = "github";
        deliveryId = "delivery-1001";
        body = PUSH;
        sign = true;
        expectedStatus = null;
        response = null;
        storedBefore = null;
        callsBefore = 0;
    }

    @Given("a source {string} with a valid signature")
    public void validSignature(String source) {
        this.source = source;
        sign = true;
        body = PUSH;
    }

    @Given("delivery {string} has not been seen")
    public void notSeen(String delivery) {
        deliveryId = delivery;
        jdbc.update("DELETE FROM idempotency_record WHERE source = ? AND key = ?", source, delivery);
    }

    @Given("a source {string} has completed delivery {string}")
    public void completed(String source, String delivery) throws Exception {
        this.source = source;
        deliveryId = delivery;
        sign = true;
        body = PUSH;
        post();
        assertEquals(200, response.statusCode());
    }

    @Given("a source {string} whose signature does not verify")
    public void badSignature(String source) {
        this.source = source;
        sign = false;
        expectedStatus = 401;
        body = PUSH;
    }

    @Given("a source {string} has delivery {string} in progress with a valid lease")
    public void inProgress(String source, String delivery) {
        this.source = source;
        deliveryId = delivery;
        body = PUSH;
        sign = true;
        var lockedUntil = clock.instant().plusMillis(90_500).truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        jdbc.update(
                """
                INSERT INTO idempotency_record
                    (source, key, fingerprint, status, locked_until, created_at)
                VALUES (?, ?, ?, 'IN_PROGRESS', ?, CURRENT_TIMESTAMP)
                """,
                source,
                delivery,
                Fingerprint.sha256Hex(PUSH),
                lockedUntil);
    }

    @Given("a source {string} whose body limit is 1 MiB")
    public void bodyLimit(String source) {
        this.source = source;
        body = new byte[BodyLimit.DEFAULT_BYTES + 1];
        sign = false;
        expectedStatus = 413;
    }

    @When("the provider delivers that webhook")
    public void deliver() throws Exception {
        post();
    }

    @When("the provider delivers a webhook")
    public void deliverAny() throws Exception {
        post();
    }

    @When("the provider delivers the same body for that delivery again")
    public void deliverSame() throws Exception {
        callsBefore = handler.deliveriesHandled();
        post();
    }

    @When("the provider delivers a different body for that same delivery")
    public void deliverDifferent() throws Exception {
        storedBefore = storedBody();
        body = OTHER;
        sign = true;
        post();
    }

    @When("the provider delivers that delivery again")
    public void deliverAgain() throws Exception {
        post();
    }

    @When("the provider delivers a body larger than that limit")
    public void deliverLarge() throws Exception {
        post();
    }

    @Then("the handler result is returned")
    public void handlerResult() {
        assertEquals(200, response.statusCode());
        assertEquals(new String(SampleWebhookHandler.ACCEPTED, StandardCharsets.UTF_8), bodyText(response.body()));
    }

    @Then("the stored handler result is returned")
    public void storedResultReturned() {
        assertEquals(200, response.statusCode());
        assertEquals(new String(SampleWebhookHandler.ACCEPTED, StandardCharsets.UTF_8), bodyText(response.body()));
    }

    @Then("the delivery is stored as completed")
    public void storedCompleted() {
        assertEquals("COMPLETED", statusOf());
        assertEquals(new String(SampleWebhookHandler.ACCEPTED, StandardCharsets.UTF_8), bodyText(storedBody()));
    }

    @Then("the handler does not run again")
    public void handlerStays() {
        assertEquals(callsBefore, handler.deliveriesHandled());
    }

    @Then("the delivery is rejected")
    public void rejected() {
        if (expectedStatus != null) {
            assertEquals(expectedStatus.intValue(), response.statusCode());
            return;
        }
        assertTrue(response.statusCode() >= 400);
    }

    @Then("no idempotency record is stored")
    public void nothingStored() {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record", Integer.class);
        assertEquals(0, count);
    }

    @Then("the delivery is rejected with HTTP 422 as a fingerprint mismatch")
    public void fingerprintMismatch() {
        assertEquals(422, response.statusCode());
    }

    @Then("the stored result is left unchanged")
    public void unchanged() {
        assertEquals("COMPLETED", statusOf());
        assertEquals(bodyText(storedBefore), bodyText(storedBody()));
    }

    @Then("the delivery is rejected until the lease ends")
    public void leaseRejects() {
        assertEquals(409, response.statusCode());
    }

    @Then("the caller is told how many whole seconds to wait")
    public void retryAfter() {
        assertEquals("91", response.headers().firstValue("retry-after").orElseThrow());
    }

    @Then("the response status is 413")
    public void status413() {
        assertEquals(413, response.statusCode());
    }

    @Given("the sample application has finished starting")
    public void started() throws Exception {
        gate.ready();
        response = probe();
        assertEquals(200, response.statusCode());
    }

    @Given("the sample application is not ready")
    public void notReady() {
        gate.notReady();
    }

    @When("a probe checks readiness")
    public void checkReadiness() throws Exception {
        response = probe();
    }

    @Then("the probe receives HTTP 200")
    public void probe200() {
        assertEquals(200, response.statusCode());
    }

    @Then("the probe reports status UP")
    public void statusUp() {
        assertEquals("UP", healthStatus(response.body()));
    }

    @Then("the probe reports status DOWN")
    public void statusDown() {
        assertEquals(503, response.statusCode());
        assertEquals("DOWN", healthStatus(response.body()));
    }

    private void post() throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/webhooks/" + source))
                .header("Content-Type", "application/json")
                .header("X-GitHub-Delivery", deliveryId)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (sign) {
            builder.header("X-Hub-Signature-256", github(body));
        } else {
            builder.header("X-Hub-Signature-256", "sha256=00");
        }
        response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> probe() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private String statusOf() {
        return jdbc.queryForObject(
                "SELECT status FROM idempotency_record WHERE source = ? AND key = ?", String.class, source, deliveryId);
    }

    private byte[] storedBody() {
        return jdbc.queryForObject(
                "SELECT response_body FROM idempotency_record WHERE source = ? AND key = ?",
                (rs, row) -> rs.getBytes("response_body"),
                source,
                deliveryId);
    }

    private static String bodyText(byte[] value) {
        return value == null ? "" : new String(value, StandardCharsets.UTF_8);
    }

    private static String healthStatus(byte[] payload) {
        var compact = new String(payload, StandardCharsets.UTF_8).replace(" ", "").replace("\n", "");
        var marker = "\"status\":\"";
        var start = compact.indexOf(marker);
        assertTrue(start >= 0);
        var from = start + marker.length();
        return compact.substring(from, compact.indexOf('"', from));
    }

    private static String github(byte[] payload) throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var raw = mac.doFinal(payload);
        var builder = new StringBuilder("sha256=");
        for (byte value : raw) {
            builder.append(Character.forDigit((value >> 4) & 0xf, 16));
            builder.append(Character.forDigit(value & 0xf, 16));
        }
        return builder.toString();
    }
}
