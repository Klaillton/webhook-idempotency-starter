package io.github.klaillton.core.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrapGateTest {

    @Test
    void shouldScoreTheCeilingWhenTheMethodIsFullyCovered() {
        assertEquals(8.0, CrapGate.score(8, 8));
        assertEquals(1.0, CrapGate.score(1, 1));
    }

    @Test
    void shouldExceedTheCeilingWhenComplexityStaysAboveEight() {
        assertEquals(9.0, CrapGate.score(9, 9));
        assertEquals(9.0, CrapGate.score(8, 6));
        assertTrue(CrapGate.score(9, 5) > CrapGate.CEILING);
    }

    @Test
    void shouldRejectTheScoreWhenCoverageIsOutsideTheComplexity() {
        assertThrows(IllegalArgumentException.class, () -> CrapGate.score(0, 0));
        assertThrows(IllegalArgumentException.class, () -> CrapGate.score(2, 3));
    }

    @Test
    void shouldReadChangedLinesWhenTheDiffAddsOrDeletes() {
        var diff = String.join(
                "\n",
                "diff --git a/core/src/main/java/io/github/klaillton/core/intake/StripeSignature.java b/core/src/main/java/io/github/klaillton/core/intake/StripeSignature.java",
                "@@ -52,0 +53,2 @@",
                "@@ -70 +70,0 @@",
                "diff --git a/README.md b/README.md",
                "@@ -1 +1,2 @@");

        var lines = CrapGate.changedLines(diff);

        assertEquals(Set.of(53, 54, 70), lines.get("io/github/klaillton/core/intake/StripeSignature.java"));
        assertEquals(1, lines.size());
    }

    @Test
    void shouldReportTheMethodWhenATouchedLineFallsInsideIt() {
        var parse = new CrapGate.Method("io/github/klaillton/core/intake/StripeSignature.java", "parse", 52, 9, 5);
        var later = new CrapGate.Method("io/github/klaillton/core/intake/StripeSignature.java", "verifies", 17, 7, 7);
        var other = new CrapGate.Method("io/github/klaillton/core/intake/HexBytes.java", "nibble", 10, 7, 7);

        var found = CrapGate.offenders(
                List.of(later, parse, other),
                CrapGate.changedLines(String.join(
                        "\n",
                        "diff --git a/core/src/main/java/io/github/klaillton/core/intake/StripeSignature.java b/core/src/main/java/io/github/klaillton/core/intake/StripeSignature.java",
                        "@@ -60 +60,1 @@")));

        assertEquals(1, found.size());
        assertTrue(found.getFirst().startsWith("io/github/klaillton/core/intake/StripeSignature.java parse "));
    }

    @Test
    void shouldIgnoreTheMethodWhenTheDiffMissesIt() {
        var method = new CrapGate.Method("io/github/klaillton/core/intake/IntakeCommand.java", "equals", 48, 11, 11);

        var found = CrapGate.offenders(
                List.of(method),
                CrapGate.changedLines(String.join(
                        "\n",
                        "diff --git a/core/src/main/java/io/github/klaillton/core/intake/HexBytes.java b/core/src/main/java/io/github/klaillton/core/intake/HexBytes.java",
                        "@@ -3 +3,1 @@")));

        assertTrue(found.isEmpty());
    }

    @Test
    void shouldReadAComplexityCounterFromTheReport() throws Exception {
        var xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="webhook-idempotency-core">
                  <package name="io/github/klaillton/core/intake">
                    <class name="io/github/klaillton/core/intake/HexBytes" sourcefilename="HexBytes.java">
                      <method name="nibble" desc="(C)I" line="12">
                        <counter type="COMPLEXITY" missed="0" covered="7"/>
                      </method>
                    </class>
                  </package>
                </report>
                """;

        var methods = CrapGate.readMethods(xml);

        assertEquals(1, methods.size());
        assertEquals("io/github/klaillton/core/intake/HexBytes.java", methods.getFirst().sourceName());
        assertEquals("nibble", methods.getFirst().name());
        assertEquals(7, methods.getFirst().complexity());
        assertEquals(7, methods.getFirst().covered());
    }
}
