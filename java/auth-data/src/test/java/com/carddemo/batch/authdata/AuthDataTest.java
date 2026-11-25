package com.carddemo.batch.authdata;

import com.carddemo.batch.common.ims.Dfsurgu0Unload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class AuthDataTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");
    private static final Path UNLOAD = Path.of("../../app/app-authorization-ims-db2-mq/data/EBCDIC",
            "AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat");
    @TempDir Path work;

    @Test
    void roundTripsUnloadAndFlatAndExportsGsam() throws Exception {
        byte[] original = Files.readAllBytes(UNLOAD);
        assertEquals(51_736, original.length);
        run(0, "load-unload", UNLOAD);
        assertEquals(21, count("pending_auth_summary", "account_id <> 0"));
        assertEquals(0, count("pending_auth_summary", "account_id = 0"));
        assertEquals(202, count("pending_auth_detail", "true"));
        assertEquals(2, count("pending_auth_unload_frame", "account_id IS NULL AND length(frame_bytes) = 88"));
        assertEquals(1, count("pending_auth_unload_frame", """
                account_id IS NULL AND length(frame_bytes) = 140
                AND substring(frame_bytes FROM 40 FOR 6) = decode('404040404040', 'hex')
                """));
        assertEquals(3, count("pending_auth_summary", """
                account_id IN (7, 13, 18) AND auth_status = repeat(chr(9216), 1)
                AND account_status_1 = repeat(chr(9216), 2) AND account_status_2 = repeat(chr(9216), 2)
                AND account_status_3 = repeat(chr(9216), 2) AND account_status_4 = repeat(chr(9216), 2)
                AND account_status_5 = repeat(chr(9216), 2)
                """));

        run(0, "export-unload", work.resolve("out.dat"));
        run(0, "export-flat", work.resolve("root.flat"), work.resolve("child.flat"));
        run(0, "export-gsam", work.resolve("root.gsam"), work.resolve("child.gsam"));
        var roots = Files.readAllBytes(work.resolve("root.flat"));
        var children = Files.readAllBytes(work.resolve("child.flat"));
        var gsam = Files.readAllBytes(work.resolve("child.gsam"));
        assertEquals(202 * 200, gsam.length);
        for (int i = 0; i < 202; i++) {
            assertArrayEquals(Arrays.copyOfRange(children, i * 206 + 6, (i + 1) * 206),
                    Arrays.copyOfRange(gsam, i * 200, (i + 1) * 200));
        }
        run(0, "load-flat", work.resolve("root.flat"), work.resolve("child.flat"));
        run(0, "export-flat", work.resolve("root.again"), work.resolve("child.again"));
        var proof = List.of(
                verify("DBPAUTP0 (21 keyed roots, 1 unkeyed root, 202 details, 2 metadata)", 226, 0,
                        original, work.resolve("out.dat")),
                verify("Flat ROOT", 21, 100, roots, work.resolve("root.again")),
                verify("Flat CHILD", 202, 206, children, work.resolve("child.again")),
                verify("GSAM ROOT vs flat ROOT", 21, 100, roots, work.resolve("root.gsam")));
        var reports = Files.createDirectories(Path.of("target/fixture-reports"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reports.resolve("reconciliation.json").toFile(),
                Map.of("status", "MATCH", "sha256", proof, "gsamChildRecords", 202, "gsamChildSha256", sha256(gsam)));
        var markdown = new StringBuilder("# G3 reconciliation: MATCH\n\n").append(
                "| Dataset | Records | Bytes | Source SHA-256 | Export SHA-256 |\n|---|---:|---:|---|---|\n");
        for (var row : proof) markdown.append("| %s | %d | %d | %s | %s |%n".formatted(
                row.dataset(), row.records(), row.bytes(), row.sourceSha256(), row.exportSha256()));
        markdown.append("\nGSAM CHILD: 202 × 200 bytes match flat CHILD payloads after removing parent keys. SHA-256: ")
                .append(sha256(gsam)).append('\n');
        Files.writeString(reports.resolve("reconciliation.md"), markdown);
        System.out.print(markdown);
    }

    @Test
    void failedLoadRollsBackAndExportUsesRelationalFields() throws Exception {
        run(0, "load-unload", UNLOAD);
        byte[] original = Files.readAllBytes(UNLOAD);
        Path broken = Files.write(work.resolve("truncated.dat"), Arrays.copyOf(original, original.length - 1));
        run(2, "load-unload", broken);
        byte[] missingDetail = new byte[original.length - 240];
        System.arraycopy(original, 0, missingDetail, 0, 228);
        System.arraycopy(original, 468, missingDetail, 228, original.length - 468);
        run(2, "load-unload", Files.write(work.resolve("missing-detail.dat"), missingDetail));
        byte[] missingRoot = new byte[original.length - 140];
        int blankRoot = original.length - 88 - 140;
        System.arraycopy(original, 0, missingRoot, 0, blankRoot);
        System.arraycopy(original, blankRoot + 140, missingRoot, blankRoot, 88);
        run(2, "load-unload", Files.write(work.resolve("missing-root.dat"), missingRoot));
        run(0, "export-unload", work.resolve("before.dat"));
        assertArrayEquals(original, Files.readAllBytes(work.resolve("before.dat")));
        try (var db = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var stmt = db.createStatement()) {
            assertEquals(1, stmt.executeUpdate("UPDATE pending_auth_summary SET credit_limit = 345.67 WHERE account_id = 1"));
        }
        run(0, "export-unload", work.resolve("updated.dat"));
        var updated = Files.readAllBytes(work.resolve("updated.dat"));
        assertFalse(Arrays.equals(original, updated));
        var in = new ByteArrayInputStream(updated);
        var codec = new Dfsurgu0Unload();
        assertInstanceOf(Dfsurgu0Unload.Metadata.class, codec.readRecord(in));
        var first = assertInstanceOf(Dfsurgu0Unload.Segment.class, codec.readRecord(in));
        assertEquals(new BigDecimal("345.67"), first.fields().get("PA-CREDIT-LIMIT"));
    }

    @Test
    void exportKeepsItsSnapshotAcrossACommittedReplacement() throws Exception {
        run(0, "load-unload", UNLOAD);
        try (var db = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = db.createStatement()) {
            db.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            db.setAutoCommit(false);
            // Take a snapshot before reading or locking either of the target tables.
            try (var rows = statement.executeQuery("SELECT count(*) FROM auth_fraud")) {
                assertTrue(rows.next());
            }
            run(0, "load-flat", Files.createFile(work.resolve("empty.root")), Files.createFile(work.resolve("empty.child")));
            assertEquals(0, count("pending_auth_summary", "true"));
            var roots = new ByteArrayOutputStream();
            var children = new ByteArrayOutputStream();
            new AuthTransfer(db).exportFlat(roots, children, false);
            assertEquals(21 * 100, roots.size());
            assertEquals(202 * 206, children.size());
            db.rollback();
        }
    }

    private static int count(String table, String where) throws Exception {
        try (var db = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var stmt = db.createStatement(); var result = stmt.executeQuery("SELECT count(*) FROM " + table + " WHERE " + where)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static void run(int expected, String command, Path... paths) {
        String[] args = Stream.concat(Stream.of(command), Arrays.stream(paths).map(Path::toString)).toArray(String[]::new);
        assertEquals(expected, AuthData.run(args, Map.of("JDBC_URL", POSTGRES.getJdbcUrl(), "JDBC_USER",
                POSTGRES.getUsername(), "JDBC_PASSWORD", POSTGRES.getPassword())), command);
    }

    record Proof(String dataset, int records, int bytes, String sourceSha256, String exportSha256) {}

    private static Proof verify(String label, int records, int width, byte[] source, Path export) throws Exception {
        if (width > 0) assertEquals(records * width, source.length, label);
        byte[] exported = Files.readAllBytes(export);
        assertArrayEquals(source, exported, label);
        return new Proof(label, records, source.length, sha256(source), sha256(exported));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
