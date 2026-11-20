package com.carddemo.batch.common.codec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Elementary fixed-width fields, including filler (needed for lossless records). */
public record RecordLayout(List<Field> fields) {
    public enum Kind { TEXT, DISPLAY, COMP3, COMP }

    public record Field(String name, Kind kind, int length, int digits, int scale, boolean signed) {
        public Field {
            if (name == null || kind == null || length < 1 || digits < 0 || scale < 0 || scale > digits) {
                throw new IllegalArgumentException("Invalid field definition");
            }
        }
    }

    public RecordLayout {
        fields = List.copyOf(fields);
        if (fields.stream().map(Field::name).distinct().count() != fields.size()) {
            throw new IllegalArgumentException("Duplicate field name");
        }
    }

    public int length() {
        return fields.stream().mapToInt(Field::length).sum();
    }

    /** Parses the elementary PIC subset used by the eleven B2 copybooks. */
    public static RecordLayout fromCopybook(Path path) throws IOException {
        var source = new StringBuilder();
        for (String raw : Files.readAllLines(path)) {
            String line = expandTabs(raw);
            if (line.length() <= 6 || line.charAt(6) == '*' || line.charAt(6) == '/') continue;
            source.append(line.substring(6, Math.min(72, line.length())).trim()).append(' ');
        }
        var item = Pattern.compile("(?i)^(\\d{2})\\s+([A-Z0-9-]+)\\s+PIC(?:TURE)?\\s+(?:IS\\s+)?([SX9V()0-9]+)(?:\\s+(?:USAGE\\s+(?:IS\\s+)?)?(COMP-3|COMP|DISPLAY))?$");
        var fields = new ArrayList<Field>();
        int offset = 0;
        for (String statement : source.toString().split("\\.")) {
            statement = statement.trim();
            if (statement.isEmpty()) continue;
            var match = item.matcher(statement);
            if (!match.matches()) {
                if (!statement.matches("(?i)\\d{2}\\s+[A-Z0-9-]+")) {
                    throw new IllegalArgumentException("Unsupported copybook clause: " + statement);
                }
                continue;
            }
            String pic = match.group(3).toUpperCase();
            boolean text = pic.contains("X");
            boolean signed = pic.startsWith("S");
            String body = pic.replace("S", "");
            String[] parts = body.split("V", -1);
            int scale = parts.length == 2 ? count(parts[1]) : 0;
            int digits = count(parts[0]) + scale;
            String usage = match.group(4);
            Kind kind = text ? Kind.TEXT : "COMP-3".equalsIgnoreCase(usage) ? Kind.COMP3
                    : "COMP".equalsIgnoreCase(usage) ? Kind.COMP : Kind.DISPLAY;
            int length = switch (kind) {
                case COMP3 -> digits / 2 + 1;
                case COMP -> digits <= 4 ? 2 : digits <= 9 ? 4 : 8;
                default -> digits;
            };
            String name = match.group(2).toUpperCase();
            fields.add(new Field(name.equals("FILLER") ? "FILLER@" + offset : name,
                    kind, length, digits, scale, signed));
            offset += length;
        }
        if (fields.isEmpty()) throw new IllegalArgumentException("No fields in " + path);
        return new RecordLayout(fields);
    }

    private static int count(String pic) {
        var matcher = Pattern.compile("[X9](?:\\((\\d+)\\))?").matcher(pic);
        int count = 0;
        while (matcher.find()) count += matcher.group(1) == null ? 1 : Integer.parseInt(matcher.group(1));
        return count;
    }

    private static String expandTabs(String raw) {
        var line = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c == '\t') line.append(" ".repeat(8 - line.length() % 8));
            else line.append(c);
        }
        return line.toString();
    }
}
