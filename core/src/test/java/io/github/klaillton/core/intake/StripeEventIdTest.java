package io.github.klaillton.core.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StripeEventIdTest {

    @Test
    void shouldReturnEmptyWhenTheBodyIsNotAJsonObject() {
        assertEquals(Optional.empty(), StripeEventId.topLevel(null));
        assertEquals(Optional.empty(), id(""));
        assertEquals(Optional.empty(), id("[]"));
        assertEquals(Optional.empty(), id("123"));
        assertEquals(Optional.empty(), id("\"id\""));
        assertEquals(Optional.empty(), id("  [ ]"));
    }

    @Test
    void shouldReturnEmptyWhenTheTopLevelIdIsMissingBlankOrNotAString() {
        assertEquals(Optional.empty(), id("{}"));
        assertEquals(Optional.empty(), id("{\"id\":\"\"}"));
        assertEquals(Optional.empty(), id("{\"id\":\"  \"}"));
        assertEquals(Optional.empty(), id("{\"id\":1}"));
        assertEquals(Optional.empty(), id("{\"id\":true}"));
        assertEquals(Optional.empty(), id("{\"id\":null}"));
        assertEquals(Optional.empty(), id("{\"id\":{\"nested\":\"evt\"}}"));
        assertEquals(Optional.empty(), id("{\"data\":{\"id\":\"inner\"}}"));
        assertEquals(Optional.of("first"), id("{\"id\":\"first\",\"id\":\"second\"}"));
    }

    @Test
    void shouldReadTheTopLevelIdWhenWhitespaceAndOtherValuesPrecedeIt() {
        assertEquals(Optional.of("evt_1"), id(" \n\t{ \"id\" : \"evt_1\" } "));
        assertEquals(
                Optional.of("evt_1"),
                id("{\"type\" : \"charge\" , \"id\" : \"evt_1\"}"));
        assertEquals(
                Optional.of("evt_1"),
                id("{\"data\":{\"id\":\"inner\",\"n\":1},\"items\":[1,\"a\",true,false,null,{\"k\":-2},[]],\"id\":\"evt_1\"}"));
        assertEquals(Optional.of("evt_1"), id("{\"n\":-12.5e+10,\"m\":1E-2,\"z\":0,\"id\":\"evt_1\"}"));
        assertEquals(Optional.of("evt_1"), id("{\"a\":true,\"b\":false,\"c\":null,\"id\":\"evt_1\"}"));
        assertEquals(Optional.of("evt_1"), id("{\"emptyObj\":{ },\"emptyArr\":[ ],\"id\":\"evt_1\"}"));
        assertEquals(Optional.of("evt\n1"), id("{\"id\":\"evt\\n1\"}"));
        assertEquals(Optional.of("\u0100"), id("{\"id\":\"\\" + "u0100\"}"));
    }

    @Test
    void shouldReturnEmptyWhenAPrecedingValueIsMalformed() {
        assertEquals(Optional.empty(), id("{\"id\" \"evt\"}"));
        assertEquals(Optional.empty(), id("{not}"));
        assertEquals(Optional.empty(), id("{\"a\":?,\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":1 \"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":1.,\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":-,\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":1e,\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":1e+,\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":tru,\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":fals,\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":nul,\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":[1 2],\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":[1,],\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":{\"b\" 1},\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"a\":{\"b\":1,},\"id\":\"evt\"}"));
        assertEquals(Optional.empty(), id("{\"id\":\"unterminated}"));
        assertEquals(Optional.empty(), id("{\"id\":\"\\q\"}"));
        assertEquals(Optional.empty(), id("{\"id\":\"\\" + "u12\"}"));
        assertEquals(Optional.empty(), id("{\"id\":\"\\" + "uXXXX\"}"));
    }

    @Test
    void shouldStopAtTheEndWhenWhitespaceHasBeenSkipped() {
        var cursor = new StripeEventId.Cursor(" \nab");

        assertFalse(cursor.done());
        cursor.skipWs();
        assertEquals('a', cursor.peek());
        assertTrue(cursor.eat('a'));
        assertFalse(cursor.eat('z'));
        assertTrue(cursor.eat('b'));
        assertTrue(cursor.done());
        assertEquals('\0', cursor.peek());
    }

    @Test
    void shouldDecodeEscapesWhenTheStringIsClosed() {
        var cursor = new StripeEventId.Cursor("\"a\\\"\\\\/\\b\\f\\n\\r\\t\\" + "u0100\"");

        assertEquals("a\"\\/\b\f\n\r\t\u0100", cursor.readString());
        assertNull(new StripeEventId.Cursor("abc").readString());
        assertNull(new StripeEventId.Cursor("\"abc").readString());
        assertNull(new StripeEventId.Cursor("\"\\q\"").readString());
        assertNull(new StripeEventId.Cursor("\"\\").readString());
        assertNull(new StripeEventId.Cursor("\"\\" + "u12\"").readString());
        assertNull(new StripeEventId.Cursor("\"\\" + "uXXXX\"").readString());
    }

    @Test
    void shouldSkipAValueWhenItIsWellFormedJson() {
        assertSkipped("-12.50e+3,", ',');
        assertSkipped("1E-2]", ']');
        assertSkipped("true,", ',');
        assertSkipped("false,", ',');
        assertSkipped("null,", ',');
        assertSkipped("[ ]x", 'x');
        assertSkipped("[]x", 'x');
        assertSkipped("[ 1 , true , \"a\" ]x", 'x');
        assertSkipped("{ }x", 'x');
        assertSkipped("{}x", 'x');
        assertSkipped("{ \"a\" : 1 , \"b\" : [ 2 ] }x", 'x');
        assertSkipped("\"ab\\n\"x", 'x');
    }

    @Test
    void shouldRejectAValueWhenItIsNotJson() {
        assertFalse(new StripeEventId.Cursor("?").skipValue());
        assertFalse(new StripeEventId.Cursor("-").skipValue());
        assertFalse(new StripeEventId.Cursor("1.").skipValue());
        assertFalse(new StripeEventId.Cursor("1e").skipValue());
        assertFalse(new StripeEventId.Cursor("1e+").skipValue());
        assertFalse(new StripeEventId.Cursor("1e-").skipValue());
        assertFalse(new StripeEventId.Cursor("tru").skipValue());
        assertFalse(new StripeEventId.Cursor("fals").skipValue());
        assertFalse(new StripeEventId.Cursor("nul").skipValue());
        assertFalse(new StripeEventId.Cursor("[").skipValue());
        assertFalse(new StripeEventId.Cursor("[1 2]").skipValue());
        assertFalse(new StripeEventId.Cursor("[1,]").skipValue());
        assertFalse(new StripeEventId.Cursor("{").skipValue());
        assertFalse(new StripeEventId.Cursor("{\"a\" 1}").skipValue());
        assertFalse(new StripeEventId.Cursor("{\"a\":1 \"b\":2}").skipValue());
        assertFalse(new StripeEventId.Cursor("{\"a\":1,}").skipValue());
        assertFalse(new StripeEventId.Cursor("{\"a\":?}").skipValue());
        assertFalse(new StripeEventId.Cursor("\"abc").skipValue());
    }

    private static void assertSkipped(String json, char next) {
        var cursor = new StripeEventId.Cursor(json);
        assertTrue(cursor.skipValue(), json);
        assertEquals(next, cursor.peek(), json);
    }

    private static Optional<String> id(String json) {
        return StripeEventId.topLevel(json.getBytes(StandardCharsets.UTF_8));
    }
}
