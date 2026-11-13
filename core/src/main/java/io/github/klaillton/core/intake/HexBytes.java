package io.github.klaillton.core.intake;

import java.util.Optional;

final class HexBytes {

    private HexBytes() {}

    static Optional<byte[]> decode(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return Optional.empty();
        }
        var out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = nibble(hex.charAt(i * 2));
            int low = nibble(hex.charAt(i * 2 + 1));
            if (high < 0 || low < 0) {
                return Optional.empty();
            }
            out[i] = (byte) ((high << 4) | low);
        }
        return Optional.of(out);
    }

    private static int nibble(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }
}
