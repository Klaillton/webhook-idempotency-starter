package io.github.klaillton.starter.http;

/** The contract does not define an HTTP status for a missing idempotency key. */
final class MissingIdempotencyKey extends RuntimeException {

    MissingIdempotencyKey() {
        super("missing idempotency key has no HTTP status");
    }
}
