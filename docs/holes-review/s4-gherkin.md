# Holes review: Gauntlet S4 scenarios

This change puts the two missing ADR-0002 scenarios into `features/ingest-webhook.feature` and runs them on Postgres through the sample app. The sample handler can fail the next delivery once, or hold it until the steps release it. Those controls are a test seam. The starter test for a handler failure now also sends the second delivery.

## Ambiguities

- The sample handler's one-shot failure and hold are there so Cucumber can see a rollback and an overlap. They are not a product API.
- The overlap step pauses 300 ms while the first handler is held, the same pause as the starter test. That pause is not a product timeout.
- Both overlapping callers receive the handler body. That is the synchronous replay of the stored result. This slice does not choose a different status for the waiter.

## Contradictions

- No contradiction with ADR-0002. The OpenAPI still has no status for a handler failure, and the new scenario does not name one.
- `docs/holes-review/s2-intake.md` said the Gherkin did not mention handler rollback. That sentence is updated. Expired-lease takeover stays out of the Gherkin.

## Missing AC

- A missing idempotency key still has no HTTP status. This slice does not map it to 400 or to 200.
- Whether the key includes a tenant is open.
- Response-body retention, truncation, and nonce storage are open. `expires_at` stays null.
- A handler failure still has no HTTP status. The scenario only requires a rejected delivery, an empty store, and a later delivery that runs the handler and stores the result.
- openapi-diff stays disabled. The skip reason is `docs/gauntlet-s4-openapi-diff.md`. This slice does not enable the gate and does not change `gauntlet.config.json`.

## Unhappy/edge

- A handler failure rolls the reservation back. A later delivery of the same key and body runs the handler and stores the result.
- Two overlapping deliveries of the same key run the handler once. Both callers receive that result, and the row is completed.
