package io.github.klaillton.core.intake;

/** Per-source raw body limit. The contract default is 1 MiB. */
public final class BodyLimit {

    public static final int DEFAULT_BYTES = 1024 * 1024;

    private BodyLimit() {}

    public static boolean exceeds(byte[] body, int limitBytes) {
        return body.length > limitBytes;
    }
}
