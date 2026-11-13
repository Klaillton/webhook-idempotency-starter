# Holes review: S2 intake core

This slice is the domain rules the merged Gherkin already fixes: body limit, HMAC, fingerprint, replay, fingerprint mismatch, and a valid lease. Postgres, Flyway, and the HTTP adapter are not in this change.

## Ambiguities

- Retry-After rounds a partial second up, so the caller waits until the lease has ended. An exact number of seconds stays unchanged. The contract says whole seconds and does not say floor or ceiling.
- Timestamp tolerance and lease length are arguments. The five-minute figure in the architecture is an example, not a fixed default.
- A generic source has no default signature scheme. Only the GitHub and Stripe schemes named in the architecture are implemented.
- The Stripe extractor reads a top-level string `id` only. A numeric `id` yields no key.
- A key longer than the OpenAPI max length has no defined status, so the domain does not invent one.

## Contradictions

- Architecture section 7 says a replay can later return only the status after response retention. This slice always replays the stored bytes. Retention is not implemented.
- Architecture section 4 specifies expired-lease takeover and rollback when the handler fails. Both are implemented here. The OpenAPI and the Gherkin do not mention them.
- `docs/sdd/Observability.md` still says `GET /health`. The contract says `GET /actuator/health`. This slice does not implement health.

## Missing AC

- A missing idempotency key still has no HTTP status. The domain stores nothing and returns no status. Reject versus process-without-idempotency stays open.
- Whether the key includes a tenant is open.
- Response-body retention, truncation, and nonce storage are open.
- Handler failure has no HTTP status. The in-progress row is removed and the exception propagates.
- Secret rotation is "any secret that has not expired". Overlap and overlap windows beyond that are not specified.

## Unhappy/edge

- A body over the source limit is rejected before the signature check and stores nothing.
- A bad or missing signature stores nothing, including when the key is also missing.
- A completed key with a different raw-body fingerprint leaves the stored result unchanged.
- A valid lease reports the whole seconds remaining and does not run the handler.
- An expired signing secret does not verify.
- A timestamp outside the supplied Stripe tolerance does not verify.
- A handler exception removes the in-progress row only while this attempt still owns that lease.
- A takeover that lands while the handler is running stays, both when the handler fails and when a late completion loses the compare-and-set.
- An expired lease is taken over and the handler runs for the new raw body.
