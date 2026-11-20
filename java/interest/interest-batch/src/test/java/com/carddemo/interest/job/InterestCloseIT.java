package com.carddemo.interest.job;

import static org.junit.jupiter.api.Assertions.*;
import com.carddemo.interest.calc.GroupWriter;
import com.carddemo.interest.calc.LegacyStatus;
import com.carddemo.interest.codec.FixedRecordCodec;
import com.carddemo.interest.codec.FixedRecordCodec.*;
import com.carddemo.interest.intake.SnapshotIntake;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = InterestCloseApplication.class)
@Import(InterestCloseIT.Hooks.class)
class InterestCloseIT {
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine");
    static { PG.start(); }
    static final Path REPO = Path.of("../../..").toAbsolutePath().normalize();
    static final List<String> CASES = List.of("i01-baseline", "i02-tie-half-cent", "i03-zero-rate-skipped",
            "i04-default-group-fallback", "i05-multi-category", "i06-negative-balance", "i07-nontie-rounding");
    static final CloseRunService.Identity ID = new CloseRunService.Identity("2022071800", "fixture-cycle", "INTCALC",
            "INT-04-working-tree", "INT-R-v1", "2022-07-05T10:00:00.00/UTC");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("interest.close.jdbc-url", PG::getJdbcUrl);
        r.add("interest.close.db-user", PG::getUsername);
        r.add("interest.close.db-password", PG::getPassword);
        r.add("interest.close.database", () -> "interest_close_it");
    }
    @Autowired CloseRunService service;
    @Autowired CloseStore store;
    @Autowired SnapshotIntake intake;
    @Autowired TestHook hook;
    @TempDir Path temp;

    static class TestHook implements GroupWriter.CommitHook {
        long before = -1;
        long after = -1;
        LegacyStatus.Failure io;
        @Override public void beforeCommit(UUID run, long ordinal) {
            if (io != null) { var failure = io; io = null; throw failure; }
            if (ordinal == before) { before = -1; throw new IllegalStateException("injected-before-commit"); }
        }
        @Override public void afterCommit(UUID run, long ordinal) {
            if (ordinal == after) { after = -1; throw new IllegalStateException("injected-after-commit"); }
        }
    }
    @TestConfiguration static class Hooks {
        @Bean @Primary TestHook testHook() { return new TestHook(); }
    }
    @BeforeEach void resetHook() { hook.before = -1; hook.after = -1; hook.io = null; }
    Path fixture(String c) { return REPO.resolve("fixtures/interest/" + c); }
    Path input(String c) { return fixture(c).resolve("input"); }
    Path out() { return temp.resolve(UUID.randomUUID().toString()); }
    Path ps(Path p, String stem) { return p.resolve("AWS.M2.CARDDEMO." + stem + ".PS.txt"); }
    Path copy(String c) throws Exception {
        Path directory = temp.resolve("input-" + UUID.randomUUID());
        Files.createDirectories(directory);
        try (var files = Files.list(input(c))) {
            for (Path p : files.toList()) Files.copy(p, directory.resolve(p.getFileName()));
        }
        return directory;
    }
    int count(String table, UUID run) {
        return store.jdbc().queryForObject("SELECT count(*) FROM interest." + table + " WHERE run_id=?", Integer.class, run);
    }
    String state(UUID run) { return store.jdbc().queryForObject("SELECT state FROM interest.run WHERE run_id=?", String.class, run); }
    String fingerprint(UUID snapshot) {
        List<String> hashes = new ArrayList<>();
        for (String table : List.of("account_input", "card_xref_input", "category_balance_input", "disclosure_rate_input")) {
            hashes.add(store.jdbc().queryForObject("SELECT count(*)::text || ':' || coalesce(md5(string_agg(encode(raw_record,'hex'),'' ORDER BY encode(raw_record,'hex'))),'') FROM interest."
                    + table + " WHERE snapshot_id=?", String.class, snapshot));
        }
        return String.join("/", hashes);
    }
    void golden(String c, Path output) throws Exception {
        for (String artifact : List.of(CloseExporter.ACCOUNT, CloseExporter.SYSTRAN, CloseExporter.SYSOUT)) {
            Path expected = fixture(c).resolve("expected").resolve(artifact);
            assertArrayEquals(Files.readAllBytes(expected), Files.readAllBytes(output.resolve(artifact)), c + " " + artifact);
        }
    }
    void unchanged(UUID snapshot, Path input) throws Exception {
        for (var pair : Map.of(Layout.ACCOUNT, "ACCTDATA", Layout.XREF, "CARDXREF", Layout.TCATBAL, "TCATBALF", Layout.DISCGRP, "DISCGRP").entrySet()) {
            var expected = SnapshotIntake.decodeLines(pair.getKey(), Files.readAllBytes(ps(input, pair.getValue()))).stream()
                    .sorted((a, b) -> FixedRecordCodec.compareKeys(a.legacyKey(), b.legacyKey())).toList();
            var actual = store.rows(snapshot, pair.getKey());
            assertEquals(expected.size(), actual.size());
            for (int i = 0; i < expected.size(); i++) assertArrayEquals(expected.get(i).original(), actual.get(i).original());
            assertEquals(java.util.HexFormat.of().formatHex(SnapshotIntake.sha256(Files.readAllBytes(ps(input, pair.getValue())))),
                    store.jdbc().queryForObject("SELECT manifest->?->>'sha256' FROM interest.snapshot WHERE snapshot_id=?", String.class, pair.getValue(), snapshot));
            for (int field = 0; field < pair.getKey().fields().size(); field++) {
                if (pair.getKey().fields().get(field).kind() != 'z') continue;
                BigDecimal total = new BigDecimal("0.00");
                for (var row : expected) total = total.add((BigDecimal) row.fields().values().get(field));
                assertEquals(total.toPlainString(), store.jdbc().queryForObject("SELECT manifest->?->'decoded_totals'->>? FROM interest.snapshot WHERE snapshot_id=?",
                        String.class, pair.getValue(), pair.getKey().fields().get(field).name(), snapshot));
            }
        }
    }

    @Test void INT_R01_INT_R02_INT_R03_INT_R04_INT_R05_INT_R06_INT_R07_INT_R08_INT_R09_INT_R10_allSevenRecordedCases() throws Exception {
        for (String c : CASES) {
            Path output = out();
            var result = service.run(input(c), output, ID, null);
            assertEquals(0, result.rc(), c + " " + result.category());
            assertEquals("COMPUTED", state(result.runId()));
            golden(c, output);
            unchanged(result.snapshotId(), input(c));
            var balances = store.rows(result.snapshotId(), Layout.TCATBAL);
            long groups = balances.stream().map(r -> ((CategoryBalance) r.fields()).acctId()).distinct().count();
            assertEquals(Math.max(0, groups - 1), count("account_delta", result.runId()));
            assertEquals(groups == 0 ? 0 : 1, count("run_checkpoint", result.runId()));
            assertEquals((long) count("interest_tx", result.runId()), store.checkpoint(result.runId()).ordinal());
            String last = ((CategoryBalance) balances.getLast().fields()).acctId();
            assertEquals(0, store.jdbc().queryForObject("SELECT count(*) FROM interest.account_delta WHERE run_id=? AND acct_id=?", Integer.class, result.runId(), last));
            System.out.println("PASS INT-04 " + c + " byte-exact datasets/SYSOUT; input digests unchanged; tx="
                    + count("interest_tx", result.runId()) + " delta=" + count("account_delta", result.runId()));
        }
    }

    @Test void INT_H06_A3_crashBeforeGroupCommitRollsBackWholeGroupAndExplicitResumeSeeksCheckpoint() throws Exception {
        hook.before = 2;
        Path output = out();
        var failed = service.run(input(CASES.getFirst()), output, ID, null);
        assertEquals(12, failed.rc()); assertEquals("FAILED", state(failed.runId()));
        assertEquals(1, count("interest_tx", failed.runId())); assertEquals(1, count("account_delta", failed.runId()));
        assertEquals(1, store.checkpoint(failed.runId()).ordinal());
        assertFalse(Files.exists(output.resolve(CloseExporter.SYSTRAN)));
        String inputs = fingerprint(failed.snapshotId());
        byte[] committed = store.jdbc().queryForObject("SELECT raw_record FROM interest.interest_tx WHERE run_id=? AND emit_ordinal=1", byte[].class, failed.runId());
        var resumed = service.run(null, output, ID, failed.runId());
        assertEquals(0, resumed.rc()); assertEquals(failed.snapshotId(), resumed.snapshotId());
        assertEquals(inputs, fingerprint(resumed.snapshotId()));
        assertArrayEquals(committed, store.jdbc().queryForObject("SELECT raw_record FROM interest.interest_tx WHERE run_id=? AND emit_ordinal=1", byte[].class, resumed.runId()));
        assertEquals(2, count("interest_tx", resumed.runId())); assertEquals(1, count("account_delta", resumed.runId()));
        golden(CASES.getFirst(), output);
    }

    @Test void INT_H06_A3_crashAfterCommitDoesNotReplayCommittedOrdinalOrClock() throws Exception {
        hook.after = 1;
        Path output = out();
        var failed = service.run(input(CASES.getFirst()), output, ID, null);
        assertEquals(12, failed.rc()); assertEquals("FAILED", state(failed.runId()));
        assertEquals(1, count("interest_tx", failed.runId())); assertEquals(1, count("account_delta", failed.runId()));
        assertEquals(1, store.checkpoint(failed.runId()).ordinal());
        var prior = store.jdbc().queryForList("SELECT emit_ordinal,amount,orig_ts,proc_ts FROM interest.interest_tx WHERE run_id=?", failed.runId());
        assertEquals(0, service.run(null, output, ID, failed.runId()).rc());
        assertEquals(prior, store.jdbc().queryForList("SELECT emit_ordinal,amount,orig_ts,proc_ts FROM interest.interest_tx WHERE run_id=? AND emit_ordinal=1", failed.runId()));
        assertEquals(2, count("interest_tx", failed.runId()));
        golden(CASES.getFirst(), output);
    }

    @Test void INT_H06_A3_resumeChangedIdentityRefusedAndNoAutomaticRestart() throws Exception {
        hook.before = 1;
        var failed = service.run(input(CASES.getFirst()), out(), ID, null);
        assertEquals("FAILED", state(failed.runId()));
        assertEquals(0, count("account_delta", failed.runId()));
        assertEquals(0, count("run_checkpoint", failed.runId()));
        for (int change = 0; change < 6; change++) {
            var values = new ArrayList<>(ID.values());
            values.set(change, change == 0 ? "abcdefghij" : "changed");
            var changed = new CloseRunService.Identity(values.get(0), values.get(1), values.get(2), values.get(3), values.get(4), values.get(5));
            assertThrows(IllegalArgumentException.class, () -> service.run(null, out(), changed, failed.runId()));
            assertEquals("FAILED", state(failed.runId()));
            assertEquals(0, count("interest_tx", failed.runId()));
        }
        var fresh = service.run(input(CASES.getFirst()), out(), ID, null);
        assertNotEquals(failed.runId(), fresh.runId());
        assertEquals("FAILED", state(failed.runId()));
        assertEquals(0, service.run(null, out(), ID, failed.runId()).rc());
        assertThrows(IllegalArgumentException.class, () -> service.run(null, out(), ID, failed.runId()));
    }

    @Test void INT_R10_INT_H08_missingAccountXrefAndDefaultAreFailedNotComputedWithU999AndNoDatasets() throws Exception {
        for (String stem : List.of("ACCTDATA", "CARDXREF", "DISCGRP")) {
            Path input = copy(CASES.getFirst()); Files.write(ps(input, stem), new byte[0]);
            Path output = out();
            var failed = service.run(input, output, ID, null);
            assertEquals(12, failed.rc()); assertEquals("REQUIRED_LOOKUP_MISSING", failed.category());
            assertEquals("FAILED", state(failed.runId()));
            assertFalse(Files.exists(output.resolve(CloseExporter.SYSTRAN)));
            String sysout = Files.readString(output.resolve(CloseExporter.SYSOUT));
            assertTrue(sysout.contains("FILE STATUS IS: NNNN0023\nABENDING PROGRAM\nCEE3ABD: USER ABEND U+000000999\n"));
            assertFalse(sysout.contains("END OF EXECUTION"));
            if (stem.equals("DISCGRP")) assertTrue(sysout.contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\nERROR READING DEFAULT DISCLOSURE GROUP"));
        }
    }

    @Test void INT_R10_malformedDuplicateAndBadGeometryIntakeIsHeldWithoutReadyRun() throws Exception {
        for (String bad : List.of("numeric", "duplicate", "geometry", "missing")) {
            Path input = copy(CASES.getFirst());
            byte[] source = Files.readAllBytes(ps(input, "TCATBALF"));
            if (bad.equals("numeric")) { source[0] = 'X'; Files.write(ps(input, "TCATBALF"), source); }
            if (bad.equals("duplicate")) Files.write(ps(input, "TCATBALF"), (new String(source, StandardCharsets.US_ASCII) + new String(source, StandardCharsets.US_ASCII)).getBytes(StandardCharsets.US_ASCII));
            if (bad.equals("geometry")) Files.writeString(ps(input, "TCATBALF"), "X".repeat(51) + "\n");
            if (bad.equals("missing")) Files.delete(ps(input, "TCATBALF"));
            var result = service.run(input, out(), ID, null);
            assertEquals(12, result.rc()); assertEquals("TARGET_INTAKE_HELD", result.category());
            assertEquals("HELD", store.jdbc().queryForObject("SELECT state FROM interest.snapshot WHERE snapshot_id=?", String.class, result.snapshotId()));
            assertEquals(0, store.jdbc().queryForObject("SELECT count(*) FROM interest.run WHERE snapshot_id=?", Integer.class, result.snapshotId()));
            assertEquals(0, store.rows(result.snapshotId(), Layout.ACCOUNT).size());
            assertTrue(store.jdbc().queryForObject("SELECT manifest->>'reason' FROM interest.snapshot WHERE snapshot_id=?", String.class, result.snapshotId()).contains("offset="));
        }
    }

    @Test void INT_R10_TN_03_TN_04_TN_07_TN_09_typedOperationFailuresRollbackAndPreserveRestrictedSourceOrder() throws Exception {
        var balances = SnapshotIntake.decodeLines(Layout.TCATBAL, Files.readAllBytes(ps(input(CASES.getFirst()), "TCATBALF")));
        String first = new String(balances.getFirst().original(), StandardCharsets.US_ASCII);
        String lookahead = new String(balances.getLast().original(), StandardCharsets.US_ASCII);
        record Probe(LegacyStatus.Dd dd, LegacyStatus.Operation operation, String status, boolean defaultRead,
                List<String> prefix, String diagnostic, String formatted) {}
        var probes = List.of(
                new Probe(LegacyStatus.Dd.DISCGRP, LegacyStatus.Operation.OPEN, "35", false, List.of(),
                        "ERROR OPENING DALY REJECTS FILE", "FILE STATUS IS: NNNN0035"),
                new Probe(LegacyStatus.Dd.XREFFILE, LegacyStatus.Operation.READ, "10", false, List.of(first),
                        "ERROR READING XREF FILE", "FILE STATUS IS: NNNN0010"),
                new Probe(LegacyStatus.Dd.TRANSACT, LegacyStatus.Operation.WRITE, "95", false, List.of(first),
                        "ERROR WRITING TRANSACTION RECORD", "FILE STATUS IS: NNNN9053"),
                new Probe(LegacyStatus.Dd.ACCTFILE, LegacyStatus.Operation.REWRITE, "99", false, List.of(first, lookahead),
                        "ERROR RE-WRITING ACCOUNT FILE", "FILE STATUS IS: NNNN9057"),
                new Probe(LegacyStatus.Dd.TCATBALF, LegacyStatus.Operation.CLOSE, "30", false, List.of(first, lookahead),
                        "ERROR CLOSING TRANSACTION BALANCE FILE", "FILE STATUS IS: NNNN0030"),
                new Probe(LegacyStatus.Dd.DISCGRP, LegacyStatus.Operation.READ, "41", true,
                        List.of(first, "DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE"),
                        "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0041"));
        for (var probe : probes) {
            hook.io = assertThrows(LegacyStatus.Failure.class, () -> LegacyStatus.check(probe.dd(), probe.operation(),
                    probe.status().getBytes(StandardCharsets.US_ASCII), LegacyStatus.Profile.LAB_ASCII, probe.defaultRead(), probe.prefix()));
            Path output = out();
            var failed = service.run(input(CASES.getFirst()), output, ID, null);
            assertEquals(12, failed.rc()); assertEquals("LEGACY_IO_FAILURE", failed.category());
            assertEquals("FAILED", state(failed.runId()));
            assertEquals(0, count("interest_tx", failed.runId())); assertEquals(0, count("account_delta", failed.runId()));
            assertEquals(0, count("run_checkpoint", failed.runId()));
            assertFalse(Files.exists(output.resolve(CloseExporter.ACCOUNT))); assertFalse(Files.exists(output.resolve(CloseExporter.SYSTRAN)));
            var expected = new ArrayList<>(List.of("START OF EXECUTION OF PROGRAM CBACT04C"));
            expected.addAll(probe.prefix());
            expected.addAll(List.of(probe.diagnostic(), probe.formatted(), "ABENDING PROGRAM", "CEE3ABD: USER ABEND U+000000999"));
            assertEquals(String.join("\n", expected) + "\n", Files.readString(output.resolve(CloseExporter.SYSOUT)));
            assertTrue(Files.readString(output.resolve("rc.json")).contains("\"STEP15\":12"));
        }
    }

    @Test void INT_H03_multiCardWithoutEvidenceHeldAndSuppliedFirstReturnNotMinimumIsSealed() throws Exception {
        Path input = copy(CASES.getFirst());
        var cards = new ArrayList<>(SnapshotIntake.decodeLines(Layout.XREF, Files.readAllBytes(ps(input, "CARDXREF"))));
        Decoded original = cards.getFirst();
        CardXref card = (CardXref) original.fields();
        byte[] second = FixedRecordCodec.encode(original, Map.of("cardNumber", "9999999999999999"));
        List<byte[]> raw = new ArrayList<>(cards.stream().map(Decoded::original).toList()); raw.add(second);
        CloseExporter.writeFixed(ps(input, "CARDXREF"), raw);
        var held = intake.load(input);
        assertFalse(held.sealed()); assertTrue(held.reason().contains("INT-H03"));
        var sealed = intake.load(input, Map.of(card.acctId(), new SnapshotIntake.FirstReturn("9999999999999999", "synthetic-host-first-return-evidence")));
        assertTrue(sealed.sealed());
        assertEquals("9999999999999999", ((CardXref) store.card(sealed.snapshotId(), card.acctId()).orElseThrow().fields()).cardNumber());
    }

    @Test void INT_Q02_INT_Q06_INT_H09_emptyAndSingleAccountNeverProduceDeltaOrClearInputs() throws Exception {
        for (boolean empty : List.of(true, false)) {
            Path input = copy("i05-multi-category");
            if (empty) Files.write(ps(input, "TCATBALF"), new byte[0]);
            var accounts = SnapshotIntake.decodeLines(Layout.ACCOUNT, Files.readAllBytes(ps(input, "ACCTDATA")));
            var raw = new ArrayList<>(accounts.stream().map(Decoded::original).toList());
            raw.add(FixedRecordCodec.encode(accounts.getFirst(), Map.of("acctId", "99999999999")));
            CloseExporter.writeFixed(ps(input, "ACCTDATA"), raw);
            var result = service.run(input, out(), ID, null);
            assertEquals(0, result.rc()); assertEquals(0, count("account_delta", result.runId()));
            assertEquals(empty ? 0 : 2, count("interest_tx", result.runId()));
            unchanged(result.snapshotId(), input);
        }
    }

    @Test void INT_Q04_INT_H04_nonzeroRateZeroAmountStillEmitsForInactiveExpiredAccount() throws Exception {
        Path input = copy("i05-multi-category");
        var accounts = SnapshotIntake.decodeLines(Layout.ACCOUNT, Files.readAllBytes(ps(input, "ACCTDATA")));
        CloseExporter.writeFixed(ps(input, "ACCTDATA"), accounts.stream().map(a -> FixedRecordCodec.encode(a,
                Map.of("activeStatus", "N", "expirationDate", "1900-01-01"))).toList());
        var balances = SnapshotIntake.decodeLines(Layout.TCATBAL, Files.readAllBytes(ps(input, "TCATBALF")));
        CloseExporter.writeFixed(ps(input, "TCATBALF"), balances.stream().map(b -> FixedRecordCodec.encode(b, Map.of("balance", new BigDecimal("0.01")))).toList());
        var result = service.run(input, out(), ID, null);
        assertEquals(0, result.rc()); assertEquals(2, count("interest_tx", result.runId()));
        assertEquals(new BigDecimal("0.00"), store.jdbc().queryForObject("SELECT sum(amount) FROM interest.interest_tx WHERE run_id=?", BigDecimal.class, result.runId()));
    }

    @Test void INT_H02_invalidParmHeldNotReady() throws Exception {
        var invalid = new CloseRunService.Identity("short", ID.cycle(), ID.feed(), ID.buildRevision(), ID.ruleRevision(), ID.clockProfile());
        var result = service.run(input(CASES.getFirst()), out(), invalid, null);
        assertEquals(12, result.rc()); assertEquals("TARGET_PARM_HELD", result.category());
        assertEquals(0, count("interest_tx", result.runId()));
    }

    @Test void A1_frozenMigrationsIdempotentWithExistingClusterRolesAndPilotSchemaUntouched() throws Exception {
        try (var source = java.sql.DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()); var statement = source.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS interest_batch");
            statement.execute("CREATE TABLE IF NOT EXISTS interest_batch.pilot_marker (n integer)");
            CloseDatabase.initialize(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword(), "second_close");
            CloseDatabase.initialize(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword(), "second_close");
            try (var rs = statement.executeQuery("SELECT to_regclass('interest_batch.pilot_marker') IS NOT NULL")) { assertTrue(rs.next()); assertTrue(rs.getBoolean(1)); }
        }
    }

    @Test void INT_H06_A3_changedSnapshotPayloadOrEncodedKeyRefusesResume() throws Exception {
        for (boolean key : List.of(true, false)) {
            hook.before = 1;
            var failed = service.run(input(CASES.getFirst()), out(), ID, null);
            var row = store.rows(failed.snapshotId(), Layout.TCATBAL).getFirst();
            if (key) {
                store.jdbc().update("UPDATE interest.category_balance_input SET legacy_key=? WHERE snapshot_id=? AND legacy_key=?",
                        "00000000000000000".getBytes(StandardCharsets.US_ASCII), failed.snapshotId(), row.legacyKey());
            } else {
                store.jdbc().update("UPDATE interest.category_balance_input SET raw_record=? WHERE snapshot_id=? AND legacy_key=?",
                        FixedRecordCodec.encode(row, Map.of("balance", new BigDecimal("0.01"))), failed.snapshotId(), row.legacyKey());
            }
            assertThrows(IllegalArgumentException.class, () -> service.run(null, out(), ID, failed.runId()));
            assertEquals("FAILED", state(failed.runId()));
            assertEquals(0, count("interest_tx", failed.runId()));
        }
    }

    @Test void TN_06_INT_R06_INT_R07_exportPatchesOnlyThreeFieldsAndPreservesDistinctiveFiller() throws Exception {
        Path input = copy(CASES.getFirst());
        var accounts = SnapshotIntake.decodeLines(Layout.ACCOUNT, Files.readAllBytes(ps(input, "ACCTDATA")));
        List<byte[]> raw = accounts.stream().map(a -> FixedRecordCodec.encode(a,
                Map.of("filler", new Bytes("#".repeat(178).getBytes(StandardCharsets.US_ASCII))))).toList();
        CloseExporter.writeFixed(ps(input, "ACCTDATA"), raw);
        Path output = out();
        var result = service.run(input, output, ID, null);
        assertEquals(0, result.rc());
        var exported = SnapshotIntake.decodeLines(Layout.ACCOUNT, Files.readAllBytes(output.resolve(CloseExporter.ACCOUNT)));
        var before = store.rows(result.snapshotId(), Layout.ACCOUNT);
        for (int i = 0; i < before.size(); i++) {
            byte[] old = before.get(i).original();
            byte[] after = exported.get(i).original();
            for (int offset = 0; offset < old.length; offset++) {
                if (i == 0 && (offset >= 12 && offset < 24 || offset >= 78 && offset < 102)) continue;
                assertEquals(old[offset], after[offset], "offset=" + offset);
            }
        }
    }
}
