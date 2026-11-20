package com.carddemo.batch.common;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class VsamTransferTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");
    static final Path ROOT = findRoot();
    @TempDir Path temporary;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load().migrate();
    }

    @TestFactory
    Stream<DynamicTest> everyInterestAndPostingMasterInput() throws Exception {
        try (var paths = Files.walk(ROOT.resolve("fixtures"))) {
            return paths.filter(Files::isRegularFile).filter(path -> {
                Path relative = ROOT.resolve("fixtures").relativize(path);
                return relative.getNameCount() == 4
                        && (relative.getName(0).toString().equals("interest") || relative.getName(0).toString().equals("posting"))
                        && relative.getName(2).toString().equals("input")
                        && VsamDataset.forInput(path.getFileName().toString()) != null;
            }).sorted().toList().stream().map(path -> DynamicTest.dynamicTest(ROOT.relativize(path).toString(), () -> {
                var dataset = VsamDataset.forInput(path.getFileName().toString());
                try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                    connection.setAutoCommit(false);
                    new VsamTransfer(connection).load(dataset, path);
                    connection.commit();
                }
                try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                    Path output = temporary.resolve("export.txt");
                    new VsamTransfer(connection).export(dataset, output);
                    assertArrayEquals(Files.readAllBytes(path), Files.readAllBytes(output));
                }
            }));
        }
    }

    @Test
    void exportsCanonicalSqlValuesInKeyOrderAndRollsBackInvalidLoads() throws Exception {
        var dataset = VsamDataset.ACCOUNT;
        Path fixture = ROOT.resolve("fixtures/interest/i01-baseline/input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt");
        byte[] first = new byte[300];
        Arrays.fill(first, (byte) ' ');
        byte[] line = Files.readAllLines(fixture, StandardCharsets.ISO_8859_1).getFirst()
                .getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(line, 0, first, 0, line.length);
        // Noncanonical positive zoned sign, plus nonblank filler.
        first[35] = '0';
        first[299] = 'Z';
        byte[] second = first.clone();
        second[10] = (byte) (first[10] + 1);
        Path input = temporary.resolve("input.txt");
        var raw = new java.io.ByteArrayOutputStream();
        raw.write(second); raw.write('\n'); raw.write(first);
        Files.write(input, raw.toByteArray());
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setAutoCommit(false);
            var transfer = new VsamTransfer(connection);
            transfer.load(dataset, input);
            try (var sql = connection.createStatement()) {
                sql.executeUpdate("UPDATE account SET current_cycle_credit = 1.23");
            }
            Path output = temporary.resolve("out.txt");
            transfer.export(dataset, output);
            byte[] actual = Files.readAllBytes(output);
            assertEquals(602, actual.length);
            var codec = new com.carddemo.batch.common.codec.FixedRecordCodec(dataset.layout.load());
            for (int row = 0; row < 2; row++) {
                byte[] source = row == 0 ? first : second;
                var values = codec.decode(source);
                values.put("ACCT-CURR-CYC-CREDIT", new java.math.BigDecimal("1.23"));
                byte[] expected = codec.encode(values);
                assertArrayEquals(expected, Arrays.copyOfRange(actual, row * 301, row * 301 + 300));
            }
            Files.write(input, new byte[301]);
            assertThrows(IllegalArgumentException.class, () -> transfer.load(dataset, input));
            transfer.export(dataset, output);
            assertArrayEquals(actual, Files.readAllBytes(output));
            Files.write(input, raw.toByteArray());
            byte[] duplicate = first.clone();
            var duplicates = new java.io.ByteArrayOutputStream();
            duplicates.write(duplicate); duplicates.write('\n'); duplicates.write(duplicate); duplicates.write('\n');
            Files.write(input, duplicates.toByteArray());
            assertThrows(java.sql.SQLException.class, () -> transfer.load(dataset, input));
            transfer.export(dataset, output);
            assertArrayEquals(actual, Files.readAllBytes(output));
            Files.write(input, new byte[0]);
            transfer.load(dataset, input);
            transfer.export(dataset, output);
            assertEquals(0, Files.size(output));
            connection.rollback();
        }
    }

    @Test
    void artifactWritersMatchGoldenFormattingAndRejectUnsafeNames() throws Exception {
        Map<String, Integer> steps = new LinkedHashMap<>();
        for (String step : new String[]{"STEP05", "STEP10", "STEP15", "STEP20", "STEP25", "STEP30"}) steps.put(step, 0);
        BatchArtifacts.writeReturnCodes(temporary, Map.of("INTCALC", steps));
        assertArrayEquals(Files.readAllBytes(ROOT.resolve("fixtures/interest/i01-baseline/expected/rc.json")),
                Files.readAllBytes(temporary.resolve("rc.json")));
        steps.put("STEP15", 4);
        BatchArtifacts.writeReturnCodes(temporary, Map.of("INTCALC", steps));
        assertTrue(Files.readString(temporary.resolve("rc.json")).contains("\"maxrc\": 4"));
        BatchArtifacts.writeReturnCodes(temporary, Map.of());
        assertEquals("{}", Files.readString(temporary.resolve("rc.json")));
        BatchArtifacts.writeSysout(temporary, "INTCALC", "STEP15", "café\n");
        assertArrayEquals("café\n".getBytes(StandardCharsets.ISO_8859_1),
                Files.readAllBytes(temporary.resolve("sysout/INTCALC.STEP15.txt")));
        BatchArtifacts.writeSysout(temporary, "INTCALC", "STEP15", "");
        assertEquals(0, Files.size(temporary.resolve("sysout/INTCALC.STEP15.txt")));
        assertThrows(IllegalArgumentException.class, () -> BatchArtifacts.writeSysout(temporary, "../bad", "STEP", ""));
        assertThrows(IllegalArgumentException.class, () -> BatchArtifacts.writeReturnCodes(temporary, Map.of("bad\"", Map.of())));
    }

    private static Path findRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("fixtures"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("Run inside the estate repository");
        return path;
    }
}
