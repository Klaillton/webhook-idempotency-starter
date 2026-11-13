# Holes review: S2 intake

This change keeps the domain rules and adds the Postgres store, Flyway, and the HTTP adapter for `POST /webhooks/{source}`. The sample app exposes `GET /actuator/health`. Cucumber runs the merged Gherkin against that app. The image digest lock and the fail-closed e2e module stay in S3.

## Ambiguities

- Retry-After rounds a partial second up, so the caller waits until the lease has ended. An exact number of seconds stays unchanged. The contract says whole seconds and does not say floor or ceiling.
- The starter defaults the lease to 2 minutes and the Stripe timestamp tolerance to 5 minutes when the application does not set them. Both are overridable. The five-minute figure in the architecture is an example, not a fixed domain rule.
- A generic source has no signature scheme. The HTTP adapter answers 401 and stores nothing. It does not invent a signature header.
- The Stripe extractor reads a top-level string `id` only. A numeric `id` yields no key.
- A key longer than the OpenAPI max length has no defined status. The column is `text`, so the adapter does not invent one.
- A source other than `github` or `stripe` has no scheme, so the adapter answers 401. That is the signature check failing closed, not a new status for an unknown name.

## Contradictions

- Architecture section 7 says a replay can later return only the status after response retention. This change stores the handler bytes and replays them. `expires_at` stays null. Retention is not implemented.
- Architecture section 4 specifies expired-lease takeover and rollback when the handler fails. Both run in the app transaction. The OpenAPI and the Gherkin do not mention them.
- `docs/sdd/Observability.md` still says `GET /health`. The sample app serves `GET /actuator/health`.

## Missing AC

- A missing idempotency key still has no HTTP status. The adapter does not map it to 400 or to 200. The store is not written.
- Whether the key includes a tenant is open.
- Response-body retention, truncation, and nonce storage are open.
- Handler failure has no HTTP status. The transaction rolls back the reservation and the handler's writes, and the exception propagates.
- Secret rotation is "any secret that has not expired". Overlap windows beyond that are not specified.
- The Cucumber "not ready" scenario is forced by a test-only health indicator. Production readiness is the actuator check, including the database.

## Unhappy/edge

- A body over the source limit is rejected with 413 before the signature check and stores nothing.
- A bad or missing signature stores nothing, including when the key is also missing.
- A completed key with a different raw-body fingerprint leaves the stored result unchanged and answers 422.
- A valid lease answers 409 with Retry-After whole seconds and does not run the handler.
- An expired signing secret does not verify.
- A timestamp outside the supplied Stripe tolerance does not verify.
- A handler exception removes the in-progress work by rolling back the transaction.
- An expired lease is taken over and the handler runs for the new raw body.
- Two overlapping deliveries of the same key run the handler once.
