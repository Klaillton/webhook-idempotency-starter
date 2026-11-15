# S3 Gates Sketch (practice branch)

Practice branch for Boot 3.5 / Java 21. Thresholds fixed in ADR-0002.

## PIT (mutation testing)

Already implemented in `core/pom.xml`. Scope: `core` only. The 80 % kill score is the CLI property `-DmutationThreshold=80`.

Do not copy a `<mutationThreshold>` or `<coverageThreshold>` element into the pom. The pom guard fails any tag whose name contains `threshold`, `minimum`, `coverage`, or `minTests`. Any `pom.xml` change also needs the human label `policy-change-approved`.

Equivalent mutants require explicit human approval with a recorded reason. The residual note in `docs/gauntlet-s3-pendencias.md` is not that approval.

## CRAP

Ceiling ≤ 8 on **touched** methods in `core` (ADR-0002). JaCoCo was not already producing a report. `core/pom.xml` now runs JaCoCo 0.8.13 (`prepare-agent`, then `report` on `verify`) and `CrapGate` reads the `COMPLEXITY` counter:

`CRAP = CC^2 * (1 - covered/CC)^3 + CC`

`mvn -pl core verify` runs the gate. `mvn test` does not. A method counts as touched when `origin/main...HEAD` or the working tree changes a line in its range. No touched methods is a pass. Git is required.

These methods already score above 8. The gate does not fail the branch while the diff leaves them alone. The next edit that touches one has to bring it back to 8 or under:

| Method | CC | Coverage | CRAP |
|---|---:|---:|---:|
| `StripeSignature.parse` | 9 | 0.56 | 16.11 |
| `IntakeCommand.equals` | 11 | 1.00 | 11.00 |
| `StripeEventId.readField` | 9 | 1.00 | 9.00 |
| `StripeEventId.Cursor.decodeEscape` | 9 | 1.00 | 9.00 |
| `StripeEventId.Cursor.skipValue` | 9 | 1.00 | 9.00 |
| `StripeEventId.Cursor.skipObject` | 8 | 0.75 | 9.00 |

## ArchUnit

Already implemented in `CoreArchitectureTest`. It imports `io.github.klaillton.core` without test classes and rejects Spring, `java.sql`, `javax.sql`, `jakarta.servlet`, `javax.servlet`, and `io.github.klaillton.starter`. Do not narrow that rule to the sketch's smaller package list.

Layers: domain (pure) → adapters (may depend on domain) → starter (autoconfiguration only).

## Blockers checked before this implementation

- A pom snippet with `<mutationThreshold>` or `<coverageThreshold>` is not usable. Threshold tags and any pom edit are policy changes.
- openapi-diff still has no runner in the pinned kit (`481ead9`). The ADR says the rule is active when that runner exists. This slice does not enable it and does not edit `gauntlet.config.json`.
- Product decisions still open in ARCHITECTURE section 10: missing idempotency key, tenant, response retention. They do not block this gate.
- Sample-app Cucumber on `main` (10 scenarios, real Postgres, Flyway v1) passed on 2026-11-15. Surefire still writes `tests="0"` for `CucumberTestEngine`. There is no separate `e2e/` module. `spring.flyway.enabled=true` is on `main` and is merged into this branch.

These three gates do not depend on sample-app. The e2e above was re-run so the "existing" claim in ADR-0002 is checked, not assumed.
