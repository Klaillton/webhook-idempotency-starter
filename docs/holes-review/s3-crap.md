# Holes review: Gauntlet S3 CRAP

This change wires JaCoCo 0.8.13 and `CrapGate` on `mvn -pl core verify`, then splits the six core methods whose CRAP was above 8. The ceiling stays in `CrapGate`: the gate fails when CRAP is greater than 8. The pom has no threshold tag. Stripe signature checks, the top-level string `id` extractor, and `IntakeCommand` equality keep the same results. `IdempotencyRecord.equals` is untouched.

## Ambiguities

- The gate scores methods touched by `origin/main...HEAD`, the unstaged diff, or the index, under `core/src/main/java`. A method the diff does not touch can stay above 8. This slice does not adopt a whole-module ceiling.
- A score of exactly 8.0 passes. `Cursor.skipValue` is 8.0 after the split. A later edit that misses one complexity point on a method of complexity 8 fails, because 8.125 is greater than 8.
- `skipObject` consumes the opening `{` without checking it. `skipValue` calls it only when the next character is `{`. A new caller that skips that check would drop one character. That is an internal precondition, not a new JSON rule.
- `parse` still ignores parts that are not `t` or `v1`. A later `t` overwrites an earlier one, and a non-numeric `t` clears the timestamp. That is the previous parser.
- PIT killed 357 of 360 mutants. The three that remain are described in `docs/gauntlet-s3-pendencias.md`. This file does not approve them as equivalent.

## Contradictions

- ADR-0002 says the openapi-diff rule is active when the runner implements it. `docs/gauntlet-s4-openapi-diff.md` still records a skip because OpenAPI v0.1 is not a stable baseline. This slice does not enable the gate, does not bump the kit pin `481ead9`, and does not edit `gauntlet.config.json`.
- An earlier sketch put threshold tags in the pom. Those tags are forbidden by the pom guard. The 80% PIT bar stays on the command line, as `-DmutationThreshold=80`.

## Missing AC

- A missing idempotency key still has no HTTP status. This slice does not map it to 400 or to 200, and the store is not written.
- Whether the key includes a tenant is open. `tenant_id` is not added.
- Response-body retention, truncation, and nonce storage are open. `expires_at` stays null.
- No acceptance criterion requires every core method to be at CRAP 8 or under when the diff does not touch it. `IdempotencyRecord.equals` stays at 8.00.
- The three PIT mutants are not approved and are not excluded.
- openapi-diff has no scenario in this slice.

## Unhappy/edge

- No touched methods is a pass. A missing `jacoco.xml` fails. If git exits non-zero, the gate fails closed with "git required for this gate".
- `mvn test` does not run the gate. `mvn -pl core verify` does.
- A null or blank Stripe header, a missing timestamp, a missing `v1`, or a non-numeric timestamp still rejects the signature. Unknown parts, empty segments, and invalid hex are ignored. The vector `t=1492774577` with body `{}` and key `whsec` still verifies.
- A non-JSON value inside an object, such as `{"a":?}`, still fails `skipValue`. The dead `if (!eat('{')) return false` branch is gone, and that mutant is gone with it.
- `skipArray` still has the unreachable `if (!eat('[')) return false`. Its `NO_COVERAGE` mutant remains.
