package com.carddemo.batch.common.codec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.json.JsonParserFactory;

import static org.junit.jupiter.api.Assertions.*;

class FixtureRoundTripTest {
    static final Path ROOT = findRoot();
    private static final Map<String, CardDemoLayout> INPUTS = Map.of(
            "ACCTDATA", CardDemoLayout.CVACT01Y, "CARDXREF", CardDemoLayout.CVACT03Y,
            "CARDDATA", CardDemoLayout.CVACT02Y, "CUSTDATA", CardDemoLayout.CVCUS01Y,
            "TCATBALF", CardDemoLayout.CVTRA01Y, "DISCGRP", CardDemoLayout.CVTRA02Y,
            "TRANTYPE", CardDemoLayout.CVTRA03Y, "TRANCATG", CardDemoLayout.CVTRA04Y,
            "DALYTRAN", CardDemoLayout.CVTRA06Y);

    @TestFactory
    Stream<DynamicTest> everyFixtureFile() throws Exception {
        try (var paths = Files.walk(ROOT.resolve("fixtures"))) {
            List<Path> files = paths.filter(Files::isRegularFile).filter(path -> {
                Path relative = ROOT.resolve("fixtures").relativize(path);
                return relative.getNameCount() >= 4 && (relative.getName(2).toString().equals("input")
                        || relative.getName(2).toString().equals("expected") && relative.getName(3).toString().equals("datasets"));
            }).sorted().toList();
            assertFalse(files.isEmpty());
            return files.stream().map(path -> DynamicTest.dynamicTest(ROOT.relativize(path).toString(), () -> {
                if (path.endsWith(Path.of("input/db/seed.sql"))) {
                    System.out.println("SKIP " + ROOT.relativize(path) + ": PostgreSQL seed script, not fixed records");
                    return;
                }
                if (path.getParent().getFileName().toString().equals("datasets")) {
                    var manifest = JsonParserFactory.getJsonParser().parseMap(
                            Files.readString(path.getParent().getParent().resolve("manifest.json")));
                    String name = path.getFileName().toString();
                    var meta = (Map<?, ?>) manifest.get(name.substring(0, name.length() - 4));
                    assertNotNull(meta, "Missing comparator manifest entry: " + path);
                    int length = ((Number) meta.get("lrecl")).intValue();
                    RecordLayout layout = outputLayout((String) meta.get("layout"), length);
                    assertEquals(length, layout.length());
                    roundTrip(path, layout, "hex".equals(meta.get("serialization")));
                } else {
                    roundTrip(path, inputLayout(path), false);
                }
            }));
        }
    }

    private RecordLayout outputLayout(String layout, int length) throws Exception {
        if (layout.equals("text")) return text(length);
        Path parityCopybook = ROOT.resolve("tools/parity/layouts").resolve(layout + ".cpy");
        if (Files.isRegularFile(parityCopybook)) return RecordLayout.fromCopybook(parityCopybook);
        for (var known : CardDemoLayout.values()) {
            if (known.name().equals(layout)) return known.load();
        }
        try (var copybooks = Files.list(ROOT.resolve("app/cpy"))) {
            Path estateCopybook = copybooks
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(layout + ".cpy"))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown fixture layout: " + layout));
            return RecordLayout.fromCopybook(estateCopybook);
        }
    }

    private RecordLayout inputLayout(Path path) throws Exception {
        String name = path.getFileName().toString();
        for (var entry : INPUTS.entrySet()) {
            if (name.startsWith("AWS.M2.CARDDEMO." + entry.getKey() + ".")) return entry.getValue().load();
        }
        if (name.equals("AWS.M2.CARDDEMO.DATEPARM.txt") || name.equals("DYNSQL.txt")) return text(80);
        if (name.equals("INPFILE.txt")) return new RecordLayout(List.of(
                new RecordLayout.Field("INPUT-TYPE", RecordLayout.Kind.TEXT, 1, 1, 0, false),
                new RecordLayout.Field("INPUT-TR-NUMBER", RecordLayout.Kind.TEXT, 2, 2, 0, false),
                new RecordLayout.Field("INPUT-TR-DESC", RecordLayout.Kind.TEXT, 50, 50, 0, false)));
        throw new AssertionError("No layout for " + path);
    }

    private static RecordLayout text(int length) {
        return new RecordLayout(List.of(new RecordLayout.Field("TEXT", RecordLayout.Kind.TEXT, length, length, 0, false)));
    }

    private void roundTrip(Path path, RecordLayout layout, boolean hexRecords) throws Exception {
        byte[] file = Files.readAllBytes(path);
        var rebuilt = new java.io.ByteArrayOutputStream();
        int start = 0;
        var codec = new FixedRecordCodec(layout);
        var hex = HexFormat.of();
        for (int end = 0; end <= file.length; end++) {
            if (end < file.length && file[end] != '\n') continue;
            if (end == file.length && start == end) break;
            byte[] line = Arrays.copyOfRange(file, start, end);
            if (hexRecords) {
                line = hex.parseHex(new String(line, StandardCharsets.US_ASCII));
                assertEquals(layout.length(), line.length, "Wrong record size: " + path);
            }
            assertTrue(line.length <= layout.length(), "Record too long: " + path);
            byte[] padded = new byte[layout.length()];
            Arrays.fill(padded, (byte) ' ');
            System.arraycopy(line, 0, padded, 0, line.length);
            var values = codec.decode(padded);
            for (var field : layout.fields()) {
                if (field.kind() == RecordLayout.Kind.TEXT) assertInstanceOf(String.class, values.get(field.name()));
                else assertEquals(field.scale(), assertInstanceOf(java.math.BigDecimal.class, values.get(field.name())).scale());
            }
            byte[] encoded = codec.encode(values);
            if (hexRecords) preserveInitializedZeros(layout, padded, encoded);
            assertArrayEquals(padded, encoded);
            if (hexRecords) rebuilt.write(hex.formatHex(encoded).getBytes(StandardCharsets.US_ASCII));
            else rebuilt.write(encoded, 0, line.length);
            if (end < file.length) rebuilt.write('\n');
            start = end + 1;
        }
        assertArrayEquals(file, rebuilt.toByteArray());
    }

    private static void preserveInitializedZeros(RecordLayout layout, byte[] original, byte[] encoded) {
        // GnuCOBOL INITIALIZE writes plain digits for signed DISPLAY zeros in the unused array entries.
        int offset = 0;
        for (var field : layout.fields()) {
            int last = offset + field.length() - 1;
            if (field.kind() == RecordLayout.Kind.DISPLAY && field.signed() && encoded[last] == '{'
                    && new String(original, offset, field.length(), StandardCharsets.US_ASCII)
                            .equals("0".repeat(field.length()))) {
                encoded[last] = '0';
            }
            offset = last + 1;
        }
    }

    @Test
    void classpathLayoutsMatchEstateAndHaveContractLengths() throws Exception {
        for (var layout : CardDemoLayout.values()) {
            String source = switch (layout) {
                case CIPAUSMY, CIPAUDTY -> "app/app-authorization-ims-db2-mq/cpy/" + layout + ".cpy";
                case AUTHFRDS -> "app/app-authorization-ims-db2-mq/dcl/AUTHFRDS.dcl";
                case REJTRAN -> "tools/parity/layouts/REJTRAN.cpy";
                default -> "app/cpy/" + layout + ".cpy";
            };
            assertEquals(RecordLayout.fromCopybook(ROOT.resolve(source)), layout.load());
        }
    }

    @Test
    void statementOutputUsesEstateCopybookWithOriginalCase() throws Exception {
        RecordLayout layout = outputLayout("COSTM01", 350);
        assertEquals(350, layout.length());
        assertEquals(RecordLayout.fromCopybook(ROOT.resolve("app/cpy/COSTM01.CPY")), layout);
    }

    @Test
    void emptyLayoutRoundTripsAnEmptyRecord() {
        var codec = new FixedRecordCodec(new RecordLayout(List.of()));
        assertArrayEquals(new byte[0], codec.encode(codec.decode(new byte[0])));
    }

    private static Path findRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("fixtures"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("Run tests inside the estate repository");
        return path;
    }
}
