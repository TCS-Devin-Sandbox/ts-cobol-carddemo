package com.carddemo.batch.common;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class BatchArtifacts {
    private BatchArtifacts() { }

    public static void writeReturnCodes(Path out, Map<String, Map<String, Integer>> jobs) throws Exception {
        var entries = new java.util.ArrayList<String>();
        for (var job : new TreeMap<>(jobs).entrySet()) {
            var steps = new TreeMap<>(job.getValue());
            String body = steps.entrySet().stream().map(step -> "   \"" + identifier(step.getKey())
                    + "\": " + step.getValue()).collect(Collectors.joining(",\n"));
            int max = steps.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            entries.add(" \"" + identifier(job.getKey()) + "\": {\n  \"maxrc\": " + max
                    + ",\n  \"steps\": {" + (body.isEmpty() ? "}" : "\n" + body + "\n  }") + "\n }");
        }
        Files.createDirectories(out);
        Files.writeString(out.resolve("rc.json"), entries.isEmpty() ? "{}" : "{\n"
                + String.join(",\n", entries) + "\n}", StandardCharsets.UTF_8);
    }

    public static void writeSysout(Path out, String job, String step, String text) throws Exception {
        Files.createDirectories(out.resolve("sysout"));
        Files.writeString(out.resolve("sysout").resolve(identifier(job) + "." + identifier(step) + ".txt"),
                text, StandardCharsets.ISO_8859_1);
    }

    private static String identifier(String value) {
        if (!value.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid job/step: " + value);
        return value;
    }
}
