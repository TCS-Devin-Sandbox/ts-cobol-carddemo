package com.carddemo.interest.gate;

import static org.junit.jupiter.api.Assertions.*;

import com.carddemo.interest.batch.InterestBatchApplication;
import com.carddemo.interest.codec.FixedRecordCodec;
import com.carddemo.interest.codec.FixedRecordCodec.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = InterestBatchApplication.class)
abstract class GateSupport {
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine");
    static { PG.start(); }
    static final Path REPO = Path.of("../../..").toAbsolutePath().normalize();
    static final String ACCOUNT = "datasets/AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.txt";
    static final String TRAN = "datasets/AWS.M2.CARDDEMO.SYSTRAN.G0001V00.txt";
    static final String INPUT_ACCOUNT = "input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt";
    static final String INPUT_BALANCE = "input/AWS.M2.CARDDEMO.TCATBALF.PS.txt";
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("interest.close.jdbc-url", PG::getJdbcUrl);
        r.add("interest.close.db-user", PG::getUsername);
        r.add("interest.close.db-password", PG::getPassword);
        r.add("interest.close.database", () -> "interest_gate_it");
    }
    @Autowired Job interestJob;
    @Autowired JobLauncher launcher;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @TempDir Path temp;

    static Path fixture(String name) { return REPO.resolve("fixtures/interest/" + name); }
    static Path specimen(String name) { return Path.of("src/test/resources/pilot/specimens", name).toAbsolutePath(); }
    JobExecution launch(Path input, Path output) throws Exception {
        return launcher.run(interestJob, new JobParametersBuilder().addString("attempt", UUID.randomUUID().toString())
                .addString("input", input.toString()).addString("output", output.toString()).toJobParameters());
    }
    UUID runId(JobExecution execution) { return UUID.fromString(execution.getExecutionContext().getString("run")); }
    int count(String table, UUID run) {
        return jdbc.queryForObject("SELECT count(*) FROM interest." + table + " WHERE run_id=?", Integer.class, run);
    }
    String state(UUID run) { return jdbc.queryForObject("SELECT state FROM interest.run WHERE run_id=?", String.class, run); }
    static List<byte[]> rows(Path file, int length) throws Exception {
        List<byte[]> records = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.US_ASCII)) {
            byte[] raw = line.getBytes(StandardCharsets.US_ASCII);
            assertTrue(raw.length <= length, file + " record=" + records.size() + " byte_offset=" + length + " overlength");
            byte[] padded = new byte[length];
            Arrays.fill(padded, (byte) ' ');
            System.arraycopy(raw, 0, padded, 0, raw.length);
            records.add(padded);
        }
        return records;
    }
    static void bytes(String label, byte[] expected, byte[] actual) {
        int offset = Arrays.mismatch(expected, actual);
        assertEquals(-1, offset, label + " byte_offset=" + offset + " (zero-based)");
    }
    static void records(String label, List<byte[]> expected, List<byte[]> actual) {
        assertEquals(expected.size(), actual.size(), label + " record=" + Math.min(expected.size(), actual.size())
                + " byte_offset=0 record count");
        for (int i = 0; i < expected.size(); i++) bytes(label + " record=" + i, expected.get(i), actual.get(i));
    }
    static Account account(byte[] raw) { return (Account) FixedRecordCodec.decode(Layout.ACCOUNT, Profile.LAB_ASCII, raw).fields(); }
    record Facts(int txCount, int deltaCount, BigDecimal txSum, BigDecimal deltaSum, String lastAccount) {}
    static Facts facts(String name) throws Exception {
        Path fixture = fixture(name);
        var transactions = rows(fixture.resolve("expected").resolve(TRAN), 350);
        BigDecimal txSum = BigDecimal.ZERO.setScale(2);
        for (byte[] raw : transactions)
            txSum = txSum.add(((InterestTransaction) FixedRecordCodec.decode(Layout.TRAN, Profile.LAB_ASCII, raw).fields()).amount());
        var before = rows(fixture.resolve(INPUT_ACCOUNT), 300);
        var after = rows(fixture.resolve("expected").resolve(ACCOUNT), 300);
        assertEquals(before.size(), after.size(), name + " golden account count");
        int deltas = 0;
        BigDecimal deltaSum = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < before.size(); i++) {
            assertEquals(account(before.get(i)).acctId(), account(after.get(i)).acctId(), name + " golden order");
            if (!Arrays.equals(before.get(i), after.get(i))) deltas++;
            deltaSum = deltaSum.add(account(after.get(i)).currentBalance().subtract(account(before.get(i)).currentBalance()));
        }
        var balances = rows(fixture.resolve(INPUT_BALANCE), 50);
        String last = ((CategoryBalance) FixedRecordCodec.decode(Layout.TCATBAL, Profile.LAB_ASCII, balances.getLast()).fields()).acctId();
        int lastIndex = -1;
        for (int i = 0; i < before.size(); i++) if (account(before.get(i)).acctId().equals(last)) lastIndex = i;
        assertTrue(lastIndex >= 0, name + " final account present in golden");
        bytes(name + " final account golden/input record=" + lastIndex, before.get(lastIndex), after.get(lastIndex));
        return new Facts(transactions.size(), deltas, txSum, deltaSum, last);
    }
    void invariants() {
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM interest.run r LEFT JOIN interest.publication p USING(run_id)
                WHERE r.state='RELEASED' AND (p.state IS DISTINCT FROM 'RECEIVED'
                  OR p.receipt->>'verified' IS DISTINCT FROM 'true')""", Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT run_id FROM interest.publication GROUP BY run_id HAVING count(*)>1) x""", Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT p.destination,r.cycle FROM interest.publication p
                JOIN interest.run r USING(run_id) WHERE r.mode='live' AND p.state IN ('PREPARED','HELD','RECEIVED')
                GROUP BY p.destination,r.cycle HAVING count(*)>1) x""", Integer.class));
    }
}
