package io.github.klaillton.core.intake;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * CRAP on core methods the diff touches. The ceiling stays here, not in the pom.
 * JaCoCo's COMPLEXITY counter supplies both the cyclomatic score and its coverage.
 */
public final class CrapGate {

    static final double CEILING = 8.0;
    private static final String SOURCE_PREFIX = "core/src/main/java/";

    private CrapGate() {}

    record Method(String sourceName, String name, int line, int complexity, int covered) {}

    static double score(int complexity, int covered) {
        if (complexity <= 0) {
            throw new IllegalArgumentException("complexity must be positive");
        }
        if (covered < 0 || covered > complexity) {
            throw new IllegalArgumentException("covered is outside the complexity");
        }
        var missed = 1.0 - (covered / (double) complexity);
        return (complexity * (double) complexity) * missed * missed * missed + complexity;
    }

    public static void main(String[] args) throws Exception {
        var root = repoRoot(Path.of("").toAbsolutePath());
        var report = root.resolve("core/target/site/jacoco/jacoco.xml");
        if (!Files.isRegularFile(report)) {
            throw new IllegalStateException("CRAP gate: missing " + report);
        }
        var touched = changedLines(diff(root));
        var found = offenders(readMethods(Files.readString(report)), touched);
        if (!found.isEmpty()) {
            throw new IllegalStateException("CRAP above 8 on touched core methods:\n" + String.join("\n", found));
        }
    }

    static List<String> offenders(List<Method> methods, Map<String, Set<Integer>> touched) {
        var bySource = new TreeMap<String, List<Method>>();
        for (var method : methods) {
            if (touched.getOrDefault(method.sourceName(), Set.of()).isEmpty()) {
                continue;
            }
            bySource.computeIfAbsent(method.sourceName(), key -> new ArrayList<>()).add(method);
        }
        var found = new ArrayList<String>();
        for (var entry : bySource.entrySet()) {
            var group = entry.getValue();
            group.sort(Comparator.comparingInt(Method::line));
            collect(group, touched.get(entry.getKey()), found);
        }
        return found;
    }

    private static void collect(List<Method> group, Set<Integer> lines, List<String> found) {
        for (var index = 0; index < group.size(); index++) {
            var method = group.get(index);
            var next = index + 1 < group.size() ? group.get(index + 1).line() : Integer.MAX_VALUE;
            if (!overlaps(method.line(), next, lines)) {
                continue;
            }
            var crap = score(method.complexity(), method.covered());
            if (crap > CEILING) {
                found.add(method.sourceName() + " " + method.name() + " " + crap);
            }
        }
    }

    static Map<String, Set<Integer>> changedLines(String diff) {
        var lines = new TreeMap<String, Set<Integer>>();
        String current = null;
        for (var raw : diff.split("\n", -1)) {
            var line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.startsWith("diff --git ")) {
                current = sourceKey(line);
                continue;
            }
            if (current == null || !line.startsWith("@@ ")) {
                continue;
            }
            lines.computeIfAbsent(current, key -> new TreeSet<>()).addAll(hunkLines(line));
        }
        return lines;
    }

    static String sourceKey(String diffLine) {
        var marker = " b/";
        var start = diffLine.lastIndexOf(marker);
        if (start < 0) {
            return null;
        }
        var path = diffLine.substring(start + marker.length());
        if (!path.startsWith(SOURCE_PREFIX) || !path.endsWith(".java")) {
            return null;
        }
        return path.substring(SOURCE_PREFIX.length());
    }

    static List<Integer> hunkLines(String header) {
        var plus = header.indexOf('+');
        var end = header.indexOf(' ', plus);
        var spec = header.substring(plus + 1, end);
        var comma = spec.indexOf(',');
        var start = Integer.parseInt(comma < 0 ? spec : spec.substring(0, comma));
        var count = comma < 0 ? 1 : Integer.parseInt(spec.substring(comma + 1));
        var marked = new ArrayList<Integer>();
        for (var offset = 0; offset < Math.max(count, 1); offset++) {
            marked.add(start + offset);
        }
        return marked;
    }

    static List<Method> readMethods(String xml) throws Exception {
        var stripped = xml.replaceFirst("<!DOCTYPE[^>]*>", "");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var builder = factory.newDocumentBuilder();
        var document = builder.parse(new InputSource(new StringReader(stripped)));
        var found = new ArrayList<Method>();
        var classes = document.getElementsByTagName("class");
        for (var index = 0; index < classes.getLength(); index++) {
            var type = (Element) classes.item(index);
            if (!(type.getParentNode() instanceof Element pack) || !"package".equals(pack.getTagName())) {
                continue;
            }
            var source = pack.getAttribute("name") + "/" + type.getAttribute("sourcefilename");
            addMethods(type, source, found);
        }
        return found;
    }

    private static void addMethods(Element type, String source, List<Method> found) {
        var nodes = type.getChildNodes();
        for (var index = 0; index < nodes.getLength(); index++) {
            if (!(nodes.item(index) instanceof Element child) || !"method".equals(child.getTagName())) {
                continue;
            }
            var method = toMethod(source, child);
            if (method != null) {
                found.add(method);
            }
        }
    }

    private static Method toMethod(String source, Element method) {
        if (!method.hasAttribute("line")) {
            return null;
        }
        var complexity = complexityOf(method);
        if (complexity == null) {
            return null;
        }
        return new Method(
                source,
                method.getAttribute("name"),
                Integer.parseInt(method.getAttribute("line")),
                complexity.total(),
                complexity.covered());
    }

    private static Complexity complexityOf(Element method) {
        var nodes = method.getChildNodes();
        for (var index = 0; index < nodes.getLength(); index++) {
            if (!(nodes.item(index) instanceof Element child) || !"counter".equals(child.getTagName())) {
                continue;
            }
            if (!"COMPLEXITY".equals(child.getAttribute("type"))) {
                continue;
            }
            var covered = Integer.parseInt(child.getAttribute("covered"));
            var total = covered + Integer.parseInt(child.getAttribute("missed"));
            if (total <= 0) {
                return null;
            }
            return new Complexity(total, covered);
        }
        return null;
    }

    private record Complexity(int total, int covered) {}

    private static boolean overlaps(int start, int next, Set<Integer> lines) {
        for (var line : lines) {
            if (line >= start && line < next) {
                return true;
            }
        }
        return false;
    }

    private static String diff(Path root) throws IOException, InterruptedException {
        return git(root, "diff", "--unified=0", "--no-color", "origin/main...HEAD", "--", "core/src/main/java")
                + git(root, "diff", "--unified=0", "--no-color", "HEAD", "--", "core/src/main/java")
                + git(root, "diff", "--unified=0", "--no-color", "--cached", "--", "core/src/main/java");
    }

    private static String git(Path root, String... args) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git required for this gate");
        }
        return output;
    }

    private static Path repoRoot(Path start) {
        var current = start;
        while (current != null && !Files.isDirectory(current.resolve(".git"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("git required for this gate");
        }
        return current;
    }
}
