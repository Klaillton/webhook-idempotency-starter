# Holes review: response retention for v0.1

ADR-0004 sets `expires_at` to `created_at` plus 7 days, stores a handler result above 64 KB as NULL, and returns an empty body on replay after 24 hours. The row stays COMPLETED. The cleanup job that deletes expired rows is not in this slice.

## Ambiguities

- 64 KB means 65536 bytes. A result of that size is stored. One byte more is stored as NULL. The first HTTP response still returns the handler bytes. Only the stored copy and later replays are empty.
- The stored body becomes NULL when a replay is served at `created_at` plus 24 hours. There is no separate nulling job. `now` equal to that instant is already expired.
- `created_at` and `expires_at` come from the intake clock, truncated to microseconds for Postgres. A row whose `expires_at` is already past still replays. Nothing deletes it.

## Contradictions

- Earlier holes reviews said `expires_at` stayed null and retention was open. Those sentences describe the slices that wrote them. This slice follows ADR-0004.
- The request body limit stays 1 MiB and still answers 413. The 64 KB limit applies to the handler result that is stored, not to the incoming webhook.

## Missing AC

- The nullable `tenant_id` column is not in this slice. The key stays `(source, key)`.
- The cleanup job that deletes rows with `expires_at` before now stays deferred. Per-source TTL is not configurable yet.
- The separate `e2e/` module, `ci/images.lock.yaml`, and openapi-diff stay out. `gauntlet.config.json` is not edited. The new scenarios run in the existing sample-app Cucumber suite.
- The oversized handler result in the sample application is a test seam, the same kind as the one-shot failure and the hold. It is not a product API.

## Unhappy/edge

- A completed delivery whose stored response is 24 hours old replays HTTP 200 with an empty body, clears `response_body`, leaves the row COMPLETED, and does not run the handler.
- A handler result above 64 KB completes the row with a NULL body. The caller receives the result once. The replay is HTTP 200 with an empty body.
- A row with `expires_at` already in the past is still replayed. The handler does not run again.
