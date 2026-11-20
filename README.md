# webhook-idempotency-starter

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![Status: pre-alpha](https://img.shields.io/badge/status-pre--alpha-lightgrey)

Spring Boot starter for exactly-once webhook processing on the JVM. It verifies the provider's HMAC signature, deduplicates deliveries by idempotency key and replays the stored response, all inside your application's own Postgres transaction. There is no extra service to run.

> **Status:** pre-alpha (`0.1.0-SNAPSHOT`). The API is not stable and nothing is published to Maven Central yet. See the [roadmap](docs/ROADMAP.md) and the [open corrections for v0.1](docs/CORRECOES-v0.1.md).

## Why

Webhook providers retry. Without idempotency, a retried `payment.succeeded` runs your handler twice. Hosted platforms such as Svix, Hookdeck, Convoy and Hook0 solve this outside your app. This starter solves it **inside** your Spring Boot app, in the same transaction as your business write, so the effect and the idempotency record commit or roll back together.

## How a delivery is handled

`POST /webhooks/{source}` with an `Idempotency-Key` header:

| Situation | Response |
|---|---|
| Signature missing or invalid, or timestamp outside tolerance | rejected before any idempotency work |
| `Idempotency-Key` missing | `400` |
| First delivery | your handler runs; its response is stored in the same transaction |
| Retry of a completed delivery | `200` with the stored response; the handler does **not** run again |
| Retry while the first delivery is still running (lease held) | `409` |
| Same key, different body | `422` |
| Handler throws | the transaction rolls back; a later retry can run |

Signature verification ships for GitHub (`X-Hub-Signature-256`) and Stripe (`Stripe-Signature`). Request bodies are limited to 1 MiB by default. Stored response bodies are kept for 24 hours and idempotency records expire after 7 days.

## Quickstart

Not on Maven Central yet. Install it locally first with `./mvnw -B -ntp install`, then add:

```xml
<dependency>
    <groupId>io.github.klaillton</groupId>
    <artifactId>webhook-idempotency-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Your app needs a Postgres `DataSource` and Flyway; the starter's migrations create its table.

Implement one handler:

```java
@Component
public class PaymentWebhookHandler implements WebhookHandler {

    @Override
    public WebhookResult handle(WebhookEvent event) {
        // event.source(), event.key(), event.body()
        // Your business write goes here: it joins the same transaction.
        return new WebhookResult("{\"accepted\":true}".getBytes(StandardCharsets.UTF_8));
    }
}
```

Configure one entry per provider:

```yaml
webhook:
  idempotency:
    lease: 2m
    timestamp-tolerance: 5m
    sources:
      github:
        secrets:
          - key: ${GITHUB_WEBHOOK_SECRET}
      stripe:
        secrets:
          - key: ${STRIPE_WEBHOOK_SECRET}
```

Never commit real secrets; load them from the environment. The [`sample-app`](sample-app) module is a runnable example with Cucumber end-to-end tests.

## Planned for v0.1

- Cleanup job for expired records.
- Read-only `GET /admin/idempotency/{source}/{key}` to inspect a key's state (secured, no payloads).
- Micrometer metrics.
- Docker Compose demo (Postgres + sample app).

Later (v0.2): admin reprocess, a webhook simulator and an outbox for outbound delivery.

## Modules

| Module | Purpose |
|---|---|
| `core` | Idempotency, HMAC verification (GitHub/Stripe) and response replay, no Spring or JDBC |
| `starter` | Spring Boot auto-configuration, HTTP endpoint, Postgres store, Flyway migrations |
| `sample-app` | Runnable demo app and end-to-end tests |

## Build

Requirements: Java 21 and a running Docker (tests use Testcontainers for Postgres).

```bash
./mvnw -B -ntp verify
```

Without Docker the integration tests fail. Do not skip them with `-DskipTests`: only exit code 0 counts.

The build also runs ArchUnit, PIT mutation testing and a CRAP score check.

## Documentation

- [Roadmap](docs/ROADMAP.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Architecture decisions](docs/adr)
- [API contract](openapi/openapi.yaml)

## Contributing

Contribuições só mediante pedido direto ao autor e contrato de cedência de direitos na forma exigida por lei.

## Support

Need help adopting this in a production fintech or payments system in the EU? Commercial setup and support will be available. Open an issue for now.

## License

Copyright (c) 2026 Klaillton Santos. All rights reserved.

No open-source license has been granted yet. A license will be chosen before the first public release.
