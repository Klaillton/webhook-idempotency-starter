package io.github.klaillton.starter.http;

import io.github.klaillton.core.intake.DeliveryKeys;
import io.github.klaillton.core.intake.IntakeCommand;
import io.github.klaillton.core.intake.IntakeDecision;
import io.github.klaillton.core.intake.SignatureScheme;
import io.github.klaillton.core.intake.SigningSecret;
import io.github.klaillton.starter.config.WebhookIdempotencyProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

    private final WebhookIntakeService intake;
    private final WebhookIdempotencyProperties properties;

    public WebhookController(WebhookIntakeService intake, WebhookIdempotencyProperties properties) {
        this.intake = intake;
        this.properties = properties;
    }

    @PostMapping("/webhooks/{source}")
    public void ingest(
            @PathVariable String source,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String githubDelivery,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String githubSignature,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        var limit = limitFor(source);
        if (request.getContentLengthLong() > limit) {
            response.setStatus(413);
            return;
        }
        var body = request.getInputStream().readNBytes(limit + 1);
        var scheme = schemeFor(source);
        if (scheme == null) {
            response.setStatus(body.length > limit ? 413 : 401);
            return;
        }
        var key = DeliveryKeys.select(source, githubDelivery, idempotencyKey, body).orElse(null);
        var command = new IntakeCommand(
                source,
                key,
                body,
                limit,
                scheme,
                signatureHeader(scheme, githubSignature, stripeSignature),
                secretsFor(source),
                toleranceFor(source),
                leaseFor(source));
        write(response, intake.receive(command));
    }

    private void write(HttpServletResponse response, IntakeDecision decision) throws IOException {
        var status = IntakeDecision.httpStatus(decision);
        if (status.isEmpty()) {
            throw new MissingIdempotencyKey();
        }
        response.setStatus(status.getAsInt());
        if (decision instanceof IntakeDecision.InProgress inProgress) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(inProgress.retryAfterSeconds()));
        }
        if (decision instanceof IntakeDecision.Completed completed) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(completed.body());
        }
    }

    private static SignatureScheme schemeFor(String source) {
        return switch (source) {
            case "github" -> SignatureScheme.GITHUB;
            case "stripe" -> SignatureScheme.STRIPE;
            default -> null;
        };
    }

    private static String signatureHeader(SignatureScheme scheme, String githubSignature, String stripeSignature) {
        return scheme == SignatureScheme.GITHUB ? githubSignature : stripeSignature;
    }

    private int limitFor(String source) {
        var configured = source(source);
        if (configured != null && configured.getBodyLimitBytes() != null) {
            return configured.getBodyLimitBytes();
        }
        return properties.getBodyLimitBytes();
    }

    private Duration leaseFor(String source) {
        var configured = source(source);
        var lease = configured == null || configured.getLease() == null ? properties.getLease() : configured.getLease();
        if (lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalStateException("lease must be positive");
        }
        return lease;
    }

    private Duration toleranceFor(String source) {
        var configured = source(source);
        if (configured != null && configured.getTimestampTolerance() != null) {
            return configured.getTimestampTolerance();
        }
        return properties.getTimestampTolerance();
    }

    private List<SigningSecret> secretsFor(String source) {
        var configured = source(source);
        if (configured == null || configured.getSecrets() == null) {
            return List.of();
        }
        var secrets = new ArrayList<SigningSecret>();
        for (var secret : configured.getSecrets()) {
            if (secret.getKey() == null) {
                continue;
            }
            secrets.add(new SigningSecret(secret.getKey().getBytes(StandardCharsets.UTF_8), secret.getExpiresAt()));
        }
        return List.copyOf(secrets);
    }

    private WebhookIdempotencyProperties.Source source(String source) {
        var sources = properties.getSources();
        if (sources == null) {
            return null;
        }
        return sources.get(source);
    }
}
