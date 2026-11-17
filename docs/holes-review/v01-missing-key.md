# Holes review: missing idempotency key is HTTP 400

ADR-0004 rejects a delivery when the configured extractor finds no key. The response is HTTP 400. The store is not written. The check runs after the signature check and before the idempotency transaction. No source processes without idempotency in v0.1.

## Ambiguities

- A missing header and a blank header are the same 400. GitHub uses `X-GitHub-Delivery`. Stripe uses a top-level string `id`. A numeric Stripe `id` still yields no key.
- The 400 response has no body. ADR-0004 does not define a problem document.
- An unknown source still answers 401, because it has no signature scheme. That happens before the key is considered. A body over the limit still answers 413 first.

## Contradictions

- Earlier holes reviews said the missing key had no HTTP status. Those sentences describe the slices that wrote them. This slice follows ADR-0004.
- 401 and 422 stay reserved for a bad signature and a fingerprint mismatch. A bad signature with no key stays 401.

## Missing AC

- The nullable `tenant_id` column is not in this slice. The key stays `(source, key)`.
- `expires_at` stays null. The 7-day TTL, the 24-hour body expiry, and the 64 KB body cap are not implemented here.
- The cleanup job that deletes expired rows stays deferred to roadmap S4.
- openapi-diff still has no runner in the pinned kit. This slice does not enable it and does not edit `gauntlet.config.json`.

## Unhappy/edge

- A verified GitHub delivery with no idempotency key returns 400, stores nothing, and does not run the handler.
- A bad signature is still rejected before the missing-key result.
- A body above the source limit is still rejected before the key is read.
