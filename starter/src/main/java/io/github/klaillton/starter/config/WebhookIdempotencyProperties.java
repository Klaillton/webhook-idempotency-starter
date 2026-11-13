package io.github.klaillton.starter.config;

import io.github.klaillton.core.intake.BodyLimit;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Adapter defaults the application can override. Lease and timestamp tolerance
 * are not architecture locks; the body limit default is the contract's 1 MiB.
 */
@ConfigurationProperties(prefix = "webhook.idempotency")
public class WebhookIdempotencyProperties {

    private int bodyLimitBytes = BodyLimit.DEFAULT_BYTES;
    private Duration lease = Duration.ofMinutes(2);
    private Duration timestampTolerance = Duration.ofMinutes(5);
    private Map<String, Source> sources = new LinkedHashMap<>();

    public int getBodyLimitBytes() {
        return bodyLimitBytes;
    }

    public void setBodyLimitBytes(int bodyLimitBytes) {
        this.bodyLimitBytes = bodyLimitBytes;
    }

    public Duration getLease() {
        return lease;
    }

    public void setLease(Duration lease) {
        this.lease = lease;
    }

    public Duration getTimestampTolerance() {
        return timestampTolerance;
    }

    public void setTimestampTolerance(Duration timestampTolerance) {
        this.timestampTolerance = timestampTolerance;
    }

    public Map<String, Source> getSources() {
        return sources;
    }

    public void setSources(Map<String, Source> sources) {
        this.sources = sources;
    }

    public static class Source {

        private Integer bodyLimitBytes;
        private Duration lease;
        private Duration timestampTolerance;
        private List<Secret> secrets = new ArrayList<>();

        public Integer getBodyLimitBytes() {
            return bodyLimitBytes;
        }

        public void setBodyLimitBytes(Integer bodyLimitBytes) {
            this.bodyLimitBytes = bodyLimitBytes;
        }

        public Duration getLease() {
            return lease;
        }

        public void setLease(Duration lease) {
            this.lease = lease;
        }

        public Duration getTimestampTolerance() {
            return timestampTolerance;
        }

        public void setTimestampTolerance(Duration timestampTolerance) {
            this.timestampTolerance = timestampTolerance;
        }

        public List<Secret> getSecrets() {
            return secrets;
        }

        public void setSecrets(List<Secret> secrets) {
            this.secrets = secrets;
        }
    }

    public static final class Secret {

        private String key = "";
        private Instant expiresAt;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }

        @Override
        public String toString() {
            var present = key != null && !key.isEmpty();
            return "Secret[present=" + present + ", expiresAt=" + expiresAt + "]";
        }
    }
}
