# Holes review: dormant tenant_id column

ADR-0004 keeps the v0.1 key as `(source, key)` and adds a nullable `tenant_id text` column. The column is unused. Multi-tenant isolation stays the application's responsibility until a later ADR.

## Ambiguities

- The column has no default other than NULL. Inserts that do not name it store NULL. Existing rows become NULL when the migration runs.
- `tenant_id` is not read or written by the intake. Two deliveries with the same source and key still collide, whatever a future caller might want a tenant to mean.
- The HTTP contract does not gain a tenant field. Activation later is a code and contract change, not another structural migration.

## Contradictions

- Earlier holes reviews said the column was out of that slice. Those sentences describe the slices that wrote them. This slice adds the column and still does not use it.
- Section 7 of ARCHITECTURE listed the table without `tenant_id`. The column list now includes it and records that v0.1 leaves it NULL.

## Missing AC

- No source, filter, or schema switch reads `tenant_id`. Per-tenant isolation is not implemented.
- The cleanup job that deletes expired rows stays deferred.
- The separate `e2e/` module and openapi-diff stay out. `gauntlet.config.json` is not edited. Gherkin and OpenAPI stay as they are because the column is not an HTTP field in v0.1.

## Unhappy/edge

- A completed delivery leaves `tenant_id` NULL.
- The column is nullable text. The primary key columns remain `source`, then `key`.
