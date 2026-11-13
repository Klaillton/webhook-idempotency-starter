package io.github.klaillton.sampleapp.e2e;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** Test-only indicator. Production readiness stays the actuator database check. */
public class ReadinessGate implements HealthIndicator {

    private final AtomicBoolean ready = new AtomicBoolean(true);

    @Override
    public Health health() {
        if (ready.get()) {
            return Health.up().build();
        }
        return Health.down().build();
    }

    void ready() {
        ready.set(true);
    }

    void notReady() {
        ready.set(false);
    }
}
