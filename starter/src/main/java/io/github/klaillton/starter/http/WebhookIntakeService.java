package io.github.klaillton.starter.http;

import io.github.klaillton.core.intake.IdempotencyStore;
import io.github.klaillton.core.intake.IntakeCommand;
import io.github.klaillton.core.intake.IntakeDecision;
import io.github.klaillton.core.intake.WebhookHandler;
import io.github.klaillton.core.intake.WebhookIntake;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** One transaction for the reservation, the handler, and the stored response. */
public class WebhookIntakeService {

    private final WebhookIntake intake;
    private final WebhookHandler handler;

    public WebhookIntakeService(IdempotencyStore store, Clock clock, WebhookHandler handler) {
        this.intake = new WebhookIntake(store, clock);
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Transactional
    public IntakeDecision receive(IntakeCommand command) {
        return intake.receive(command, handler);
    }
}
