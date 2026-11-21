package com.carddemo.batch.dataload;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.carddemo.batch.common.VsamDataset;
import com.carddemo.batch.common.VsamTransfer;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
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
class DataLoadTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:15-alpine");
    static final ObjectMapper JSON = new ObjectMapper();
    static final AtomicInteger FILES = new AtomicInteger(), RECORDS = new AtomicInteger(), FIELDS = new AtomicInteger();
    @TempDir Path temporary;

    @BeforeAll static void migrate() {
        Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()).load().migrate();
    }

    @TestFactory Stream<DynamicTest> everySupportedFixtureInputAsEbcdicUnload() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("fixtures"))) root = root.getParent();
        final Path fixtures = root.resolve("fixtures");
        try (var paths = Files.walk(fixtures)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> fixtures.relativize(p).getNameCount() == 4 && p.getParent().getFileName().toString().equals("input"))
                    .filter(p -> VsamDataset.forInput(p.getFileName().toString()) != null).sorted().toList().stream()
                    .map(path -> DynamicTest.dynamicTest(fixtures.relativize(path).toString(), () -> {
                        var dataset = VsamDataset.forInput(path.getFileName().toString());
                        Path unload = temporary.resolve("unload.bin");
                        int count = convert(path, unload, dataset);
                        try (var connection = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
                            var report = DataLoad.execute(connection, temporary, List.of(new DataLoad.Entry("unload.bin", dataset)), false);
                            assertEquals("MATCH", report.status());
                            var result = report.datasets().getFirst();
                            assertEquals(count, result.sourceCount());
                            assertEquals(count, result.databaseCount());
                            assertTrue(result.fields().stream().allMatch(f -> f.status().equals("MATCH")));
                            Path export = temporary.resolve("export.txt");
                            new VsamTransfer(connection).export(dataset, export);
                            assertArrayEquals(Files.readAllBytes(path), Files.readAllBytes(export));
                            DataLoad.writeReport(Path.of("target/fixture-reports").resolve(fixtures.relativize(path)), report);
                            FILES.incrementAndGet(); RECORDS.addAndGet(count); FIELDS.addAndGet(result.fields().size());
                            System.out.printf("FIXTURE %s source=%d database=%d fields=%d MATCH%n", fixtures.relativize(path), count, count, result.fields().size());
                        }
                    }));
        }
    }

    @AfterAll static void summary() {
        System.out.printf("FIXTURE SUMMARY files=%d records=%d field-checksums=%d MATCH%n", FILES.get(), RECORDS.get(), FIELDS.get());
    }

    @Test void cliDetectsCorruptionAndHandlesEmptyAndInvalidInputsAtomically() throws Exception {
        Path manifest = temporary.resolve("manifest.json"), unload = temporary.resolve("source.bin"), reports = temporary.resolve("reports");
        var dataset = VsamDataset.TYPE;
        var codec = new FixedRecordCodec(dataset.layout.load(), Charset.forName("IBM037"));
        var record = new java.util.LinkedHashMap<String, Object>();
        for (var field : dataset.layout.load().fields()) record.put(field.name(), "");
        record.put("TRAN-TYPE", "01"); record.put("TRAN-TYPE-DESC", "SYNTHETIC");
        Files.write(unload, codec.encode(record));
        JSON.writeValue(manifest.toFile(), List.of(new DataLoad.Entry("source.bin", dataset)));
        var env = Map.of("JDBC_URL", DB.getJdbcUrl(), "JDBC_USER", DB.getUsername(), "JDBC_PASSWORD", DB.getPassword());
        String[] args = {temporary.toString(), manifest.toString(), reports.toString()};
        assertEquals(0, DataLoad.run(args, env));
        try (var connection = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()); var sql = connection.createStatement()) {
            sql.executeUpdate("UPDATE tran_type SET description = 'CORRUPTED'");
        }
        assertEquals(1, DataLoad.run(new String[]{args[0], args[1], args[2], "--verify-only"}, env));
        var mismatch = JSON.readTree(reports.resolve("reconciliation.json").toFile());
        assertEquals("MISMATCH", mismatch.path("status").asText());
        assertTrue(Files.readString(reports.resolve("reconciliation.md")).contains("TRAN-TYPE-DESC"));
        var fields = mismatch.path("datasets").get(0).path("fields");
        assertEquals(1, Stream.of(JSON.treeToValue(fields, DataLoad.FieldResult[].class)).filter(f -> f.status().equals("MISMATCH")).count());
        System.out.println("NEGATIVE corrupted TRAN-TYPE-DESC: MISMATCH exit=1");

        Files.write(unload, new byte[]{1});
        assertEquals(2, DataLoad.run(args, env));
        try (var connection = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()); var sql = connection.createStatement();
                var rows = sql.executeQuery("SELECT description FROM tran_type")) {
            assertTrue(rows.next()); assertEquals("CORRUPTED", rows.getString(1).stripTrailing());
        }
        Files.write(unload, new byte[0]);
        assertEquals(0, DataLoad.run(args, env));
        assertEquals(0, JSON.readTree(reports.resolve("reconciliation.json").toFile()).path("datasets").get(0).path("sourceCount").asInt());
        JSON.writeValue(manifest.toFile(), List.of(new DataLoad.Entry("source.bin", dataset), new DataLoad.Entry("source.bin", dataset)));
        assertEquals(2, DataLoad.run(args, env));
        JSON.writeValue(manifest.toFile(), List.of());
        assertEquals(2, DataLoad.run(args, env));
        JSON.writeValue(manifest.toFile(), List.of(new DataLoad.Entry("../outside", dataset)));
        assertEquals(2, DataLoad.run(args, env));
        assertEquals(2, DataLoad.run(new String[0], env));
    }

    private static int convert(Path source, Path output, VsamDataset dataset) throws Exception {
        var layout = dataset.layout.load();
        var latin = new FixedRecordCodec(layout);
        var ebcdic = new FixedRecordCodec(layout, Charset.forName("IBM037"));
        var bytes = new ByteArrayOutputStream();
        int count = 0;
        for (String line : Files.readAllLines(source, StandardCharsets.ISO_8859_1)) {
            byte[] padded = new byte[layout.length()];
            Arrays.fill(padded, (byte) ' ');
            byte[] raw = line.getBytes(StandardCharsets.ISO_8859_1);
            System.arraycopy(raw, 0, padded, 0, raw.length);
            bytes.write(ebcdic.encode(latin.decode(padded)));
            count++;
        }
        Files.write(output, bytes.toByteArray());
        return count;
    }
}
