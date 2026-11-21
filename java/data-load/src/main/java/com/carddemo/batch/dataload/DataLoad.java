package com.carddemo.batch.dataload;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.carddemo.batch.common.VsamDataset;
import com.carddemo.batch.common.VsamTransfer;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;

public final class DataLoad {
    private static final ObjectMapper JSON = new ObjectMapper();
    public record Entry(String file, VsamDataset dataset) {}
    public record FieldResult(String field, String source, String database, String status) {}
    public record DatasetResult(String file, String dataset, String layout, long sourceCount,
                                long databaseCount, String status, List<FieldResult> fields) {}
    public record Report(String status, String checksumAlgorithm, List<DatasetResult> datasets) {}

    public static void main(String[] args) {
        System.exit(run(args, System.getenv()));
    }

    public static int run(String[] args, Map<String, String> environment) {
        if (args.length < 3 || args.length > 4 || (args.length == 4 && !args[3].equals("--verify-only"))) {
            System.err.println("Usage: data-load INPUT_DIRECTORY MANIFEST.json REPORT_DIRECTORY [--verify-only]");
            return 2;
        }
        Path output = Path.of(args[2]);
        try {
            var entries = JSON.readValue(Path.of(args[1]).toFile(), Entry[].class);
            String url = required(environment, "JDBC_URL");
            String user = required(environment, "JDBC_USER");
            String password = required(environment, "JDBC_PASSWORD");
            String schema = environment.getOrDefault("JDBC_SCHEMA", "public");
            Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).load().migrate();
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setSchema(schema);
                var report = execute(connection, Path.of(args[0]), List.of(entries), args.length == 4);
                writeReport(output, report);
                for (var dataset : report.datasets()) {
                    System.out.printf("%s %s source=%d database=%d fields=%d %s%n", dataset.file(),
                            dataset.dataset(), dataset.sourceCount(), dataset.databaseCount(), dataset.fields().size(), dataset.status());
                }
                System.out.println("Overall: " + report.status());
                return report.status().equals("MATCH") ? 0 : 1;
            }
        } catch (Exception failure) {
            try { writeReport(output, new Report("ERROR", "SHA-256 multiset sum modulo 2^256", List.of())); }
            catch (Exception reportFailure) { System.err.println("Could not write error report"); }
            System.err.println("Data load/report failed (" + failure.getClass().getSimpleName() + "). Database errors roll back; report I/O errors may occur after commit. Check manifest, data, output permissions and database access.");
            return 2;
        }
    }

    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    public static Report execute(Connection connection, Path input, List<Entry> entries, boolean verifyOnly) throws Exception {
        if (!connection.getAutoCommit()) throw new IllegalStateException("Requires an idle connection");
        if (entries.isEmpty()) throw new IllegalArgumentException("Empty manifest");
        Path root = input.toRealPath();
        var datasets = new HashSet<VsamDataset>();
        var paths = new ArrayList<Path>();
        for (var entry : entries) {
            if (entry.dataset() == null || !datasets.add(entry.dataset())) throw new IllegalArgumentException("Duplicate/missing dataset");
            Path path = root.resolve(entry.file()).toRealPath();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IllegalArgumentException("Unsafe input path");
            paths.add(path);
        }
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setAutoCommit(false);
        try {
            var results = new ArrayList<DatasetResult>();
            var transfer = new VsamTransfer(connection);
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                var source = new Checksums(entry.dataset());
                try (var records = new Records(paths.get(i), entry.dataset())) {
                    if (verifyOnly) {
                        for (var values = records.next(); values != null; values = records.next()) source.add(values);
                    } else {
                        transfer.loadRecords(entry.dataset(), () -> {
                            var values = records.next();
                            if (values != null) source.add(values);
                            return values;
                        });
                    }
                }
                var database = new Checksums(entry.dataset());
                transfer.readRecords(entry.dataset(), database::add);
                var fields = new ArrayList<FieldResult>();
                for (var field : source.sums.keySet()) {
                    String a = source.hex(field), b = database.hex(field);
                    fields.add(new FieldResult(field, a, b, a.equals(b) ? "MATCH" : "MISMATCH"));
                }
                boolean match = source.count == database.count && fields.stream().allMatch(f -> f.status().equals("MATCH"));
                results.add(new DatasetResult(entry.file(), entry.dataset().name(), entry.dataset().layout.name(),
                        source.count, database.count, match ? "MATCH" : "MISMATCH", fields));
            }
            boolean match = results.stream().allMatch(r -> r.status().equals("MATCH"));
            if (match && !verifyOnly) connection.commit(); else connection.rollback();
            return new Report(match ? "MATCH" : "MISMATCH", "SHA-256 multiset sum modulo 2^256; UTF-8 canonical decoded values", results);
        } catch (Exception failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public static void writeReport(Path directory, Report report) throws Exception {
        Files.createDirectories(directory);
        JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("reconciliation.json").toFile(), report);
        var text = new StringBuilder("# VSAM reconciliation: " + report.status() + "\n\n" + report.checksumAlgorithm() + "\n");
        for (var dataset : report.datasets()) {
            text.append("\n## ").append(dataset.file()).append(" — ").append(dataset.status())
                    .append("\n\nLayout: ").append(dataset.layout()).append("; source records: ").append(dataset.sourceCount())
                    .append("; database records: ").append(dataset.databaseCount()).append("\n\n| Copybook field | Source | PostgreSQL | Status |\n|---|---|---|---|\n");
            for (var field : dataset.fields()) text.append("| ").append(field.field()).append(" | ").append(field.source())
                    .append(" | ").append(field.database()).append(" | ").append(field.status()).append(" |\n");
        }
        Files.writeString(directory.resolve("reconciliation.md"), text);
    }

    private static final class Records implements AutoCloseable {
        private final InputStream input;
        private final FixedRecordCodec codec;
        private final int width;
        Records(Path path, VsamDataset dataset) throws Exception {
            var layout = dataset.layout.load();
            width = layout.length();
            codec = new FixedRecordCodec(layout, Charset.forName("IBM037"));
            input = Files.newInputStream(path);
        }
        Map<String, Object> next() throws Exception {
            byte[] bytes = input.readNBytes(width);
            if (bytes.length == 0) return null;
            if (bytes.length != width) throw new IllegalArgumentException("Partial fixed record");
            return codec.decode(bytes);
        }
        public void close() throws java.io.IOException { input.close(); }
    }

    private static final class Checksums {
        private static final BigInteger MODULUS = BigInteger.ONE.shiftLeft(256);
        final Map<String, BigInteger> sums = new LinkedHashMap<>();
        final FixedRecordCodec canonical;
        long count;
        Checksums(VsamDataset dataset) throws Exception {
            var layout = dataset.layout.load();
            canonical = new FixedRecordCodec(layout);
            for (var field : layout.fields()) sums.put(field.name(), BigInteger.ZERO);
        }
        void add(Map<String, Object> record) throws Exception {
            var values = canonical.decode(canonical.encode(record));
            for (var field : sums.keySet()) {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(values.get(field).toString().getBytes(StandardCharsets.UTF_8));
                sums.put(field, sums.get(field).add(new BigInteger(1, hash)).mod(MODULUS));
            }
            count++;
        }
        String hex(String field) { return String.format("%064x", sums.get(field)); }
    }
}
