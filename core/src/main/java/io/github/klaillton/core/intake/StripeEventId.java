package io.github.klaillton.core.intake;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Stripe's default extractor: the top-level string field {@code id}. */
public final class StripeEventId {

    private StripeEventId() {}

    public static Optional<String> topLevel(byte[] body) {
        if (body == null) {
            return Optional.empty();
        }
        var cursor = new Cursor(new String(body, StandardCharsets.UTF_8));
        cursor.skipWs();
        if (!cursor.eat('{')) {
            return Optional.empty();
        }
        while (true) {
            var field = readField(cursor);
            if (field.stop()) {
                return field.id() == null || field.id().isBlank() ? Optional.empty() : Optional.of(field.id());
            }
        }
    }

    private static Field readField(Cursor cursor) {
        cursor.skipWs();
        if (cursor.eat('}')) {
            return Field.end();
        }
        var key = cursor.readString();
        cursor.skipWs();
        if (key == null || !cursor.eat(':')) {
            return Field.end();
        }
        if ("id".equals(key)) {
            cursor.skipWs();
            if (cursor.peek() != '"') {
                return Field.end();
            }
            return Field.found(cursor.readString());
        }
        if (!cursor.skipValue()) {
            return Field.end();
        }
        cursor.skipWs();
        if (cursor.eat('}')) {
            return Field.end();
        }
        if (!cursor.eat(',')) {
            return Field.end();
        }
        return Field.more();
    }

    private record Field(String id, boolean stop) {
        static Field found(String id) {
            return new Field(id, true);
        }

        static Field end() {
            return new Field(null, true);
        }

        static Field more() {
            return new Field(null, false);
        }
    }

    static final class Cursor {
        private final String text;
        private int index;

        Cursor(String text) {
            this.text = text;
        }

        boolean done() {
            return index >= text.length();
        }

        char peek() {
            return done() ? '\0' : text.charAt(index);
        }

        void skipWs() {
            while (!done() && Character.isWhitespace(peek())) {
                index++;
            }
        }

        boolean eat(char expected) {
            if (peek() != expected) {
                return false;
            }
            index++;
            return true;
        }

        String readString() {
            if (!eat('"')) {
                return null;
            }
            var out = new StringBuilder();
            while (!done()) {
                var c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                var decoded = decodeEscape();
                if (decoded == null) {
                    return null;
                }
                out.append(decoded);
            }
            return null;
        }

        boolean skipValue() {
            skipWs();
            return switch (peek()) {
                case '"' -> readString() != null;
                case '{' -> skipObject();
                case '[' -> skipArray();
                case 't' -> skipLiteral("true");
                case 'f' -> skipLiteral("false");
                case 'n' -> skipLiteral("null");
                case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> skipNumber();
                default -> false;
            };
        }

        private boolean skipObject() {
            if (!eat('{')) {
                return false;
            }
            skipWs();
            if (eat('}')) {
                return true;
            }
            while (true) {
                skipWs();
                if (readString() == null) {
                    return false;
                }
                skipWs();
                if (!eat(':') || !skipValue()) {
                    return false;
                }
                skipWs();
                if (eat('}')) {
                    return true;
                }
                if (!eat(',')) {
                    return false;
                }
            }
        }

        private boolean skipArray() {
            if (!eat('[')) {
                return false;
            }
            skipWs();
            if (eat(']')) {
                return true;
            }
            while (true) {
                if (!skipValue()) {
                    return false;
                }
                skipWs();
                if (eat(']')) {
                    return true;
                }
                if (!eat(',')) {
                    return false;
                }
            }
        }

        private boolean skipLiteral(String literal) {
            if (!text.startsWith(literal, index)) {
                return false;
            }
            index += literal.length();
            return true;
        }

        private boolean skipNumber() {
            eat('-');
            if (!eatDigits()) {
                return false;
            }
            if (eat('.') && !eatDigits()) {
                return false;
            }
            return !(eat('e') || eat('E')) || eatExponent();
        }

        private boolean eatExponent() {
            if (peek() == '+' || peek() == '-') {
                index++;
            }
            return eatDigits();
        }

        private boolean eatDigits() {
            if (!Character.isDigit(peek())) {
                return false;
            }
            while (Character.isDigit(peek())) {
                index++;
            }
            return true;
        }

        private String decodeEscape() {
            if (done()) {
                return null;
            }
            var esc = text.charAt(index++);
            return switch (esc) {
                case '"', '\\', '/' -> String.valueOf(esc);
                case 'b' -> "\b";
                case 'f' -> "\f";
                case 'n' -> "\n";
                case 'r' -> "\r";
                case 't' -> "\t";
                case 'u' -> unicode();
                default -> null;
            };
        }

        private String unicode() {
            if (index + 4 > text.length()) {
                return null;
            }
            var hex = HexBytes.decode(text.substring(index, index + 4));
            if (hex.isEmpty() || hex.get().length != 2) {
                return null;
            }
            index += 4;
            var decoded = hex.get();
            var code = ((decoded[0] & 0xff) << 8) | (decoded[1] & 0xff);
            return Character.toString((char) code);
        }
    }
}
