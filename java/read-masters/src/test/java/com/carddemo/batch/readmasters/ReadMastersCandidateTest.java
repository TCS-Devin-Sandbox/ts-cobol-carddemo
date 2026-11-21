package com.carddemo.batch.readmasters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.UUID;

import com.carddemo.batch.common.VsamTransfer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ReadMastersCandidateTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");
    @TempDir Path temp;
    private static final String PREFIX = "AWS.M2.CARDDEMO.";

    private static Path fixtures() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("fixtures/read-masters"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Cannot find repository fixtures");
        return root.resolve("fixtures/read-masters");
    }

    @Test void populatedAndEmptyMatchEveryGoldenArtifact() throws Exception {
        for (String name : new String[]{"r01-populated", "r02-empty"}) {
            Path fixture = fixtures().resolve(name);
            Path out = temp.resolve(name);
            run(fixture.resolve("input"), out);
            try (var files = Files.walk(fixture.resolve("expected"))) {
                for (Path expected : files.filter(Files::isRegularFile).toList()) {
                    Path relative = fixture.resolve("expected").relativize(expected);
                    if (relative.toString().equals("manifest.json")) continue;
                    if (relative.toString().equals("rc.json")) {
                        assertEquals(Files.readString(expected).replaceAll("\\s", ""),
                                Files.readString(out.resolve(relative)).replaceAll("\\s", ""));
                    } else assertArrayEquals(Files.readAllBytes(expected), Files.readAllBytes(out.resolve(relative)),
                            name + "/" + relative);
                }
            }
        }
    }

    @Test void indexedReadsSortByPrimaryKey() throws Exception {
        Path input = copyInput();
        Path file = input.resolve(PREFIX + "CARDDATA.PS.txt");
        var rows = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        Files.writeString(file, rows.getLast() + "\n" + rows.getFirst() + "\n",
                StandardCharsets.ISO_8859_1);
        run(input, temp.resolve("out"));
        assertEquals("START OF EXECUTION OF PROGRAM CBACT02C\n" + pad(rows.getFirst(), 150) + "\n" + pad(rows.getLast(), 150)
                + "\nEND OF EXECUTION OF PROGRAM CBACT02C\n",
                Files.readString(temp.resolve("out/sysout/READCARD.STEP05.txt"), StandardCharsets.ISO_8859_1));
    }

    @Test void packedDebitRetainsPriorValueWhenSourceDebitIsNonzero() throws Exception {
        Path input = copyInput();
        Path file = input.resolve(PREFIX + "ACCTDATA.PS.txt");
        var rows = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        String second = rows.get(1);
        second = second.substring(0, 90) + "00000000010{" + second.substring(102);
        Files.writeString(file, rows.getFirst() + "\n" + second + "\n", StandardCharsets.ISO_8859_1);
        run(input, temp.resolve("out"));
        var exported = Files.readAllLines(temp.resolve("out/datasets/" + PREFIX + "ACCTDATA.PSCOMP.txt"));
        byte[] record = HexFormat.of().parseHex(exported.get(1));
        assertEquals("0000000252500c", HexFormat.of().formatHex(record, 90, 97));
    }

    @Test void missingFileAbendsOnlyThatJobAndKeepsOtherJobsRunning() throws Exception {
        Path input = copyInput();
        Files.delete(input.resolve(PREFIX + "CARDDATA.PS.txt"));
        Path out = temp.resolve("out");
        run(input, out);
        assertEquals("START OF EXECUTION OF PROGRAM CBACT02C\nERROR OPENING CARDFILE\n"
                + "FILE STATUS IS: NNNN0035\nABENDING PROGRAM\nCEE3ABD: USER ABEND U+000000999\n",
                Files.readString(out.resolve("sysout/READCARD.STEP05.txt")));
        assertTrue(Files.readString(out.resolve("rc.json")).contains("\"STEP05\": 12"));
        assertTrue(Files.readString(out.resolve("sysout/READCUST.STEP05.txt"))
                .endsWith("END OF EXECUTION OF PROGRAM CBCUS01C\n"));
    }

    @Test void predelRemovesStaleAccountExportsBeforeMissingInputAbendOnRerun() throws Exception {
        Path input = copyInput();
        Path out = temp.resolve("out");
        run(input, out);
        for (String suffix : new String[]{"PSCOMP", "ARRYPS", "VBPS"}) {
            assertTrue(Files.size(out.resolve("datasets/" + PREFIX + "ACCTDATA." + suffix + ".txt")) > 0);
        }
        Path unrelated = out.resolve("datasets/unrelated.txt");
        Files.writeString(unrelated, "keep");
        Files.delete(input.resolve(PREFIX + "ACCTDATA.PS.txt"));
        run(input, out);
        for (String suffix : new String[]{"PSCOMP", "ARRYPS", "VBPS"}) {
            assertFalse(Files.exists(out.resolve("datasets/" + PREFIX + "ACCTDATA." + suffix + ".txt")));
        }
        assertEquals("keep", Files.readString(unrelated));
        String sysout = Files.readString(out.resolve("sysout/READACCT.STEP05.txt"));
        assertTrue(sysout.contains("ERROR OPENING ACCTFILE\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM\n"));
        String codes = Files.readString(out.resolve("rc.json"));
        assertTrue(codes.contains("\"PREDEL\": 0"));
        assertTrue(codes.contains("\"STEP05\": 12"));
    }

    @Test void rejectsOversizeInputThroughSharedLoaderAndDropsOwnedSchema() throws Exception {
        Path input = copyInput();
        Files.writeString(input.resolve(PREFIX + "CARDDATA.PS.txt"), "z".repeat(160) + "\n");
        var failure = assertThrows(IllegalArgumentException.class, () -> run(input, temp.resolve("out")));
        assertTrue(failure.getMessage().contains("Record too long"));
        assertNoCandidateSchemas();
    }

    @Test void programsReadBusinessColumnsFromPostgresRatherThanFixtureFiles() throws Exception {
        String schema = "store_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setSchema(schema);
        admin.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(dataSource).defaultSchema(schema).load().migrate();
            Path input = copyInput();
            try (var connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                var transfer = new VsamTransfer(connection);
                for (var master : ReadMaster.values()) {
                    transfer.load(master.dataset, input.resolve(PREFIX + master.dataset.inputName + ".PS.txt"));
                }
                connection.commit();
            }
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.update("UPDATE account SET current_balance=-123.45 WHERE account_id=1001");
            jdbc.update("UPDATE card SET embossed_name='PG CARD' WHERE account_id=1001");
            jdbc.update("UPDATE card_xref SET customer_id=42 WHERE account_id=1001");
            jdbc.update("UPDATE customer SET first_name='PG CUSTOMER'");
            try (var files = Files.list(input)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Path out = temp.resolve("out");
            for (var master : ReadMaster.values()) {
                var parameters = new org.springframework.batch.core.JobParametersBuilder()
                        .addString("out", out.toString()).addString("missingInput", "false").toJobParameters();
                var step = new org.springframework.batch.core.JobExecution(1L, parameters).createStepExecution("STEP05");
                new ReadMasterTasklet(master, new ReadMasterStore(jdbc)).execute(step.createStepContribution(), null);
                String text = Files.readString(out.resolve("sysout/" + master.job + ".STEP05.txt"), StandardCharsets.ISO_8859_1);
                switch (master) {
                    case ACCOUNT -> assertTrue(text.contains("ACCT-CURR-BAL           :000000012345-"));
                    case CARD -> assertTrue(text.contains("PG CARD"));
                    case XREF -> assertTrue(text.contains("000000042"));
                    case CUSTOMER -> assertTrue(text.contains("PG CUSTOMER"));
                }
            }
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private static void run(Path input, Path out) throws Exception {
        ReadMastersCandidate.run(input, out, POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        assertNoCandidateSchemas();
    }

    private static void assertNoCandidateSchemas() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM information_schema.schemata "
                + "WHERE schema_name LIKE 'read_masters_%'", Integer.class));
    }

    @Test void rejectsWrongChainAndMalformedArguments() {
        assertThrows(IllegalArgumentException.class, () -> ReadMastersCandidate.main(new String[]{"--chain"}));
        assertThrows(IllegalArgumentException.class, () -> ReadMastersCandidate.main(new String[]{"--chain", "posting"}));
        assertThrows(IllegalArgumentException.class, () -> ReadMastersCandidate.main(new String[]{"--chain", "read-masters"}));
    }

    private Path copyInput() throws Exception {
        Path input = Files.createDirectory(temp.resolve("input"));
        try (var files = Files.list(fixtures().resolve("r01-populated/input"))) {
            for (Path file : files.toList()) Files.copy(file, input.resolve(file.getFileName()));
        }
        return input;
    }

    private static String pad(String value, int length) {
        return (value + " ".repeat(length)).substring(0, length);
    }
}
