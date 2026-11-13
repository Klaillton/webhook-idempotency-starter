package io.github.klaillton.core.intake;

import java.util.Arrays;
import java.util.OptionalInt;

/**
 * Result of one intake attempt. {@link MissingKey} has no HTTP status: the
 * contract does not define that response.
 */
public sealed interface IntakeDecision
        permits IntakeDecision.Completed, IntakeDecision.Rejected, IntakeDecision.InProgress, IntakeDecision.MissingKey {

    record Completed(byte[] body, boolean replay) implements IntakeDecision {
        public Completed {
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Completed(byte[] otherBody, boolean otherReplay)
                    && Arrays.equals(body, otherBody)
                    && replay == otherReplay;
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(body) + Boolean.hashCode(replay);
        }

        @Override
        public String toString() {
            return "Completed[bodyLength=" + body.length + ", replay=" + replay + "]";
        }
    }

    record Rejected(Reason reason) implements IntakeDecision {}

    record InProgress(long retryAfterSeconds) implements IntakeDecision {}

    record MissingKey() implements IntakeDecision {}

    enum Reason {
        BODY_TOO_LARGE,
        SIGNATURE,
        FINGERPRINT
    }

    static OptionalInt httpStatus(IntakeDecision decision) {
        return switch (decision) {
            case Completed ignored -> OptionalInt.of(200);
            case Rejected(var reason) -> OptionalInt.of(status(reason));
            case InProgress ignored -> OptionalInt.of(409);
            case MissingKey ignored -> OptionalInt.empty();
        };
    }

    private static int status(Reason reason) {
        return switch (reason) {
            case BODY_TOO_LARGE -> 413;
            case SIGNATURE -> 401;
            case FINGERPRINT -> 422;
        };
    }

    static boolean sameBody(byte[] left, byte[] right) {
        return Arrays.equals(left, right);
    }
}
