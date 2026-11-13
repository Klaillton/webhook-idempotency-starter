package io.github.klaillton.starter.config;

import io.github.klaillton.core.intake.IdempotencyStore;
import io.github.klaillton.core.intake.WebhookHandler;
import io.github.klaillton.starter.http.WebhookController;
import io.github.klaillton.starter.http.WebhookIntakeService;
import io.github.klaillton.starter.store.PostgresIdempotencyStore;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(JdbcTemplate.class)
@EnableConfigurationProperties(WebhookIdempotencyProperties.class)
public class WebhookIdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    IdempotencyStore idempotencyStore(JdbcTemplate jdbcTemplate) {
        return new PostgresIdempotencyStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    WebhookIntakeService webhookIntakeService(IdempotencyStore store, Clock clock, WebhookHandler handler) {
        return new WebhookIntakeService(store, clock, handler);
    }

    @Bean
    @ConditionalOnMissingBean
    WebhookController webhookController(WebhookIntakeService intake, WebhookIdempotencyProperties properties) {
        return new WebhookController(intake, properties);
    }
}
