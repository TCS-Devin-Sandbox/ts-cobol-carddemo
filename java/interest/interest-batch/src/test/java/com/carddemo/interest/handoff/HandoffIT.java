package com.carddemo.interest.handoff;

import static org.junit.jupiter.api.Assertions.*;

import com.carddemo.interest.codec.FixedRecordCodec;
import com.carddemo.interest.codec.FixedRecordCodec.Layout;
import com.carddemo.interest.codec.FixedRecordCodec.Profile;
import com.carddemo.interest.handoff.HandoffOutcome.Held;
import com.carddemo.interest.handoff.HandoffOutcome.Released;
import com.carddemo.interest.handoff.HandoffOutcome.ShadowRehearsed;
import com.carddemo.interest.handoff.HostBridge.ApplyStatus;
import com.carddemo.interest.handoff.HostBridge.CatalogStatus;
import com.carddemo.interest.handoff.HostBridge.Fence;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * INT-05 acceptance on PostgreSQL 15: i01-shaped COMPUTED rows -> deterministic packet, simulated fail-closed
 * handoff. Seeding runs as the owner; the handoff runs under SET ROLE interest_publish (A1 grants).
 */
class HandoffIT {
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine");
    static final Path I01 = locate("fixtures/interest/i01-baseline");
    static final String DESTINATION = "AWS.M2.CARDDEMO.SYSTRAN";
    static final AtomicInteger CYCLES = new AtomicInteger();
    static JdbcTemplate owner;
    static JdbcTemplate publisher;
    static TransactionTemplate publishTx;

    static {
        PG.start();
    }

    UUID run;
    String cycle;
    SimulatedHost host;
    final List<String> signals = new ArrayList<>();
    HandoffCoordinator coordinator;

    @BeforeAll
    static void migrate() throws Exception {
        SingleConnectionDataSource ownerDs = new SingleConnectionDataSource(PG.getJdbcUrl(), PG.getUsername(),
                PG.getPassword(), true);
        for (String file : List.of("V001__interest.sql", "V002__batch_repository.sql")) {
            ScriptUtils.executeSqlScript(ownerDs.getConnection(), new ClassPathResource("db/migration/" + file));
        }
        owner = new JdbcTemplate(ownerDs);
        SingleConnectionDataSource publishDs = new SingleConnectionDataSource(PG.getJdbcUrl(), PG.getUsername(),
                PG.getPassword(), true);
        publisher = new JdbcTemplate(publishDs);
        publisher.execute("SET ROLE interest_publish");
        publishTx = new TransactionTemplate(new DataSourceTransactionManager(publishDs));
        System.out.println("DB " + owner.queryForObject("SELECT version()", String.class));
        System.out.println("ROLE " + publisher.queryForObject("SELECT current_user", String.class));
    }

    @BeforeEach
    void seedLiveRun() throws Exception {
        cycle = "2022-07-C" + CYCLES.incrementAndGet();
        run = seed("live", cycle);
        host = hostFor(DESTINATION, cycle);
        coordinator = coordinator(host);
    }

    @AfterEach
    void sqlInvariants() {
        Integer releasedWithoutReceipt = owner.queryForObject("""
                SELECT count(*) FROM interest.run r LEFT JOIN interest.publication p USING (run_id)
                WHERE r.state = 'RELEASED' AND (p.state IS DISTINCT FROM 'RECEIVED'
                  OR (p.receipt->>'verified') IS DISTINCT FROM 'true')""", Integer.class);
        Integer duplicateClaims = owner.queryForObject("""
                SELECT count(*) FROM (SELECT p.destination, r.cycle FROM interest.publication p JOIN interest.run r
                  USING (run_id) WHERE r.mode = 'live' GROUP BY 1, 2 HAVING count(*) > 1) d""", Integer.class);
        Integer rowsForRun = owner.queryForObject("SELECT count(*) FROM interest.publication WHERE run_id = ?",
                Integer.class, run);
        System.out.println("SQL released_without_verified_receipt=" + releasedWithoutReceipt
                + " live_claims_per_destination_cycle_gt1=" + duplicateClaims + " publication_rows_for_run=" + rowsForRun);
        assertEquals(0, releasedWithoutReceipt);
        assertEquals(0, duplicateClaims);
        assertTrue(rowsForRun <= 1);
    }

    @Test
    void INT_R08_packetIsDeterministicWithTwoOrdered350ByteRecordsExactDigestAndOneGuardedDelta() throws Exception {
        PacketBuilder builder = new PacketBuilder(publisher);
        ExportPacket first = builder.build(run);
        ExportPacket second = builder.build(run);
        byte[] payload = first.payload();
        assertArrayEquals(payload, second.payload());
        assertEquals(first.manifest(), second.manifest());
        assertEquals(2, first.recordCount());
        assertEquals(700, payload.length);
        List<byte[]> golden = fixtureRecords("expected/datasets/AWS.M2.CARDDEMO.SYSTRAN.G0001V00.txt", 350);
        for (int i = 0; i < 2; i++) {
            assertEquals(i + 1, first.records().get(i).ordinal());
            assertArrayEquals(golden.get(i), java.util.Arrays.copyOfRange(payload, i * 350, (i + 1) * 350));
        }
        assertEquals(List.of("2022071800000001", "2022071800000002"),
                first.records().stream().map(ExportPacket.PayloadRecord::txnId).toList());
        assertArrayEquals(Digests.sha256(payload), first.payloadDigest());
        assertEquals(1, first.deltas().size());
        ExportPacket.GuardedDelta delta = first.deltas().get(0);
        assertEquals("00000002001", delta.acctId());
        assertArrayEquals(Digests.sha256(fixtureRecords("input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt", 300).get(0)),
                delta.preimageFingerprint());
        assertArrayEquals(fixtureRecords("expected/datasets/AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.txt", 300).get(0),
                delta.postImage());
        assertTrue(first.manifest().contains("\"record_count\":2"));
        assertTrue(first.manifest().contains("\"payload_sha256\":\"" + first.payloadDigestHex() + "\""));
        System.out.println("PACKET records=" + first.recordCount() + " bytes=" + payload.length + " payload_sha256="
                + first.payloadDigestHex() + " manifest_sha256=" + HexFormat.of().formatHex(first.manifestDigest())
                + " deltas=" + first.deltas().size());
        System.out.println("MANIFEST " + first.manifest().replace(run.toString(), "<run>")
                .replaceAll("\"snapshot_id\":\"[^\"]+\"", "\"snapshot_id\":\"<snapshot>\"")
                .replace(cycle, "<cycle>"));
    }

    @Test
    void shadowModeRecordsPrivatePacketWithoutHostCallsOrSuccessCondition() throws Exception {
        UUID shadow = seed("shadow", cycle);
        SimulatedHost untouched = hostFor(DESTINATION, cycle);
        HandoffCoordinator c = coordinator(untouched);
        HandoffOutcome first = c.handoff(shadow);
        HandoffOutcome again = c.handoff(shadow);
        assertInstanceOf(ShadowRehearsed.class, first);
        assertEquals(first, again);
        assertEquals(0, untouched.hostCalls);
        assertTrue(signals.isEmpty());
        assertEquals("PRIVATE", publicationState(shadow));
        assertEquals("COMPUTED", runState(shadow));
        assertEquals("shadow:" + DESTINATION, owner.queryForObject(
                "SELECT destination FROM interest.publication WHERE run_id = ?", String.class, shadow));
        assertEquals(1, (int) owner.queryForObject("SELECT count(*) FROM interest.publication WHERE run_id = ?",
                Integer.class, shadow));
        assertEquals(HandoffCoordinator.SHADOW_GENERATION, owner.queryForObject(
                "SELECT current_generation FROM interest.publication WHERE run_id = ?", String.class, shadow));
        report("shadow", shadow, first, untouched);
    }

    @Test
    void verifiedMatchingReceiptReleasesOnceAndCatalogsExactlyOneGeneration() throws Exception {
        byte[] payload = new PacketBuilder(publisher).build(run).payload();
        HandoffOutcome outcome = coordinator.handoff(run);
        assertEquals(new Released(run, DESTINATION + ".G0001V00", false), outcome);
        assertEquals(List.of(DESTINATION + ".G0001V00"), host.generations);
        assertArrayEquals(payload,
                host.generationBytes.get(DESTINATION + ".G0001V00"));
        assertArrayEquals(fixtureRecords("expected/datasets/AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.txt", 300).get(0),
                host.accounts.get("00000002001"));
        assertEquals(List.of(run + "|" + DESTINATION + ".G0001V00"), signals);
        assertEquals("RECEIVED", publicationState(run));
        assertEquals("RELEASED", runState(run));
        assertEquals("true", owner.queryForObject("SELECT receipt->>'verified' FROM interest.publication WHERE run_id = ?",
                String.class, run));
        assertEquals(2L, owner.queryForObject("SELECT record_count FROM interest.publication WHERE run_id = ?", Long.class, run));
        report("verified-receipt", run, outcome);
    }

    @Test
    void INT_H06_repeatedVerifiedReceiptDoesNotDuplicateGenerationOrSuccessCondition() throws Exception {
        coordinator.handoff(run);
        int mutations = host.mutations;
        HandoffOutcome again = coordinator.handoff(run);
        HandoffOutcome resolved = coordinator.resolve(run);
        assertEquals(new Released(run, DESTINATION + ".G0001V00", true), again);
        assertEquals(again, resolved);
        assertEquals(mutations, host.mutations);
        assertEquals(1, host.generations.size());
        assertEquals(1, signals.size());
        report("repeat-receipt", run, again);
    }

    @Test
    void INT_R06_wrongAccountPreimageHoldsBeforeAnyMutation() throws Exception {
        byte[] changed = host.accounts.get("00000002001").clone();
        changed[299] = 'X';
        host.accounts.put("00000002001", changed);
        assertHeldWithoutMutation(HoldReason.PREIMAGE_MISMATCH, "wrong-preimage");
    }

    @Test
    void missingFenceHoldsBeforeAnyMutation() throws Exception {
        host.fence = Optional.empty();
        assertHeldWithoutMutation(HoldReason.FENCE_MISSING, "missing-fence");
    }

    @Test
    void staleFenceForAnotherCycleHoldsBeforeAnyMutation() throws Exception {
        host.fence = Optional.of(new Fence("F-old", DESTINATION, "2022-06", true));
        assertHeldWithoutMutation(HoldReason.FENCE_STALE, "stale-fence-cycle");
    }

    @Test
    void inactiveFenceHoldsBeforeAnyMutation() throws Exception {
        host.fence = Optional.of(new Fence("F-" + cycle, DESTINATION, cycle, false));
        assertHeldWithoutMutation(HoldReason.FENCE_STALE, "inactive-fence");
    }

    @Test
    void fenceLostDuringHandoffHoldsBeforeAnyMutation() throws Exception {
        host.afterPreflight = () -> host.fence = Optional.of(new Fence("F-other", DESTINATION, cycle, true));
        assertHeldWithoutMutation(HoldReason.FENCE_STALE, "fence-changed");
    }

    @Test
    void INT_H05_backupTransactionIdCollisionHoldsBeforeAnyMutation() throws Exception {
        host.existingIds.add("2022071800000002");
        assertHeldWithoutMutation(HoldReason.TXN_ID_COLLISION, "txn-id-collision");
    }

    @Test
    void movedCurrentGenerationHoldsWithoutCatalog() throws Exception {
        host.afterPreflight = () -> host.generations.add(DESTINATION + ".G0001V00");
        HandoffOutcome outcome = coordinator.handoff(run);
        assertEquals(new Held(run, HoldReason.GENERATION_MOVED), outcome);
        assertEquals(0, host.catalogCalls);
        assertHeld(HoldReason.GENERATION_MOVED);
        report("generation-moved", run, outcome);
    }

    @Test
    void INT_H06_missingReceiptAfterApplyAndCatalogHoldsUntilVerifiedReceiptArrives() throws Exception {
        host.receiptMode = SimulatedHost.ReceiptMode.NONE;
        HandoffOutcome outcome = coordinator.handoff(run);
        assertEquals(new Held(run, HoldReason.RECEIPT_MISSING), outcome);
        assertHeld(HoldReason.RECEIPT_MISSING);
        int mutations = host.mutations;
        assertEquals(outcome, coordinator.handoff(run));
        assertEquals(mutations, host.mutations);
        host.receiptMode = SimulatedHost.ReceiptMode.RECORD;
        assertEquals(new Released(run, DESTINATION + ".G0001V00", false), coordinator.resolve(run));
        assertEquals(1, host.generations.size());
        assertEquals(1, signals.size());
        report("missing-receipt", run, outcome);
    }

    @Test
    void INT_H06_mismatchedReceiptHoldsWithoutSuccessCondition() throws Exception {
        host.receiptMode = SimulatedHost.ReceiptMode.WRONG_DIGEST;
        HandoffOutcome outcome = coordinator.handoff(run);
        assertEquals(new Held(run, HoldReason.RECEIPT_MISMATCH), outcome);
        assertEquals(outcome, coordinator.resolve(run));
        assertHeld(HoldReason.RECEIPT_MISMATCH);
        report("receipt-mismatch", run, outcome);
    }

    @Test
    void INT_H06_partialAccountApplyHoldsWithoutCatalogOrReapply() throws Exception {
        UUID twoDeltas = seed("live", cycle + "-P");
        owner.update("""
                INSERT INTO interest.account_delta SELECT ?, acct_id, current_balance, current_balance + 1.00,
                  cycle_credit, cycle_credit, cycle_debit, cycle_debit, sha256(raw_record)
                FROM interest.account_input a JOIN interest.run r USING (snapshot_id)
                WHERE r.run_id = ? AND a.acct_id = '00000002002'""", twoDeltas, twoDeltas);
        SimulatedHost partial = hostFor(DESTINATION, cycle + "-P");
        partial.applyFault.put("00000002002", ApplyStatus.PRECONDITION_FAILED);
        HandoffCoordinator c = coordinator(partial);
        HandoffOutcome outcome = c.handoff(twoDeltas);
        assertEquals(new Held(twoDeltas, HoldReason.APPLY_UNCERTAIN), outcome);
        assertEquals(0, partial.catalogCalls);
        assertTrue(partial.generations.isEmpty());
        assertArrayEquals(fixtureRecords("expected/datasets/AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.txt", 300).get(0),
                partial.accounts.get("00000002001"));
        int mutations = partial.mutations;
        assertEquals(new Held(twoDeltas, HoldReason.RECEIPT_MISSING), c.handoff(twoDeltas));
        assertEquals(mutations, partial.mutations);
        assertEquals("HELD", publicationState(twoDeltas));
        assertEquals("HELD", runState(twoDeltas));
        assertTrue(signals.isEmpty());
        report("partial-apply", twoDeltas, outcome, partial);
    }

    @Test
    void INT_H06_unknownApplyOutcomeHoldsWithoutCatalog() throws Exception {
        host.applyFault.put("00000002001", ApplyStatus.UNKNOWN);
        host.applyFaultStillWrites.put("00000002001", true);
        HandoffOutcome outcome = coordinator.handoff(run);
        assertEquals(new Held(run, HoldReason.APPLY_UNCERTAIN), outcome);
        assertEquals(0, host.catalogCalls);
        assertHeld(HoldReason.APPLY_UNCERTAIN);
        report("unknown-apply", run, outcome);
    }

    @Test
    void INT_H06_ambiguousCatalogStaysHeldUntilVerifiedReceiptThenReleasesOneGeneration() throws Exception {
        host.catalogReply = CatalogStatus.UNKNOWN;
        host.catalogWritesAnyway = true;
        HandoffOutcome outcome = coordinator.handoff(run);
        assertEquals(new Held(run, HoldReason.CATALOG_AMBIGUOUS), outcome);
        assertHeld(HoldReason.CATALOG_AMBIGUOUS);
        assertTrue(signals.isEmpty());
        assertEquals(new Released(run, DESTINATION + ".G0001V00", false), coordinator.resolve(run));
        assertEquals(1, host.catalogCalls);
        assertEquals(List.of(DESTINATION + ".G0001V00"), host.generations);
        assertEquals(1, signals.size());
        report("ambiguous-catalog", run, outcome);
    }

    @Test
    void INT_H06_failedCatalogWithoutReceiptStaysHeld() throws Exception {
        host.catalogReply = CatalogStatus.FAILED;
        HandoffOutcome outcome = coordinator.handoff(run);
        assertEquals(new Held(run, HoldReason.CATALOG_AMBIGUOUS), outcome);
        assertEquals(new Held(run, HoldReason.RECEIPT_MISSING), coordinator.resolve(run));
        assertEquals(1, host.catalogCalls);
        assertTrue(host.generations.isEmpty());
        assertTrue(signals.isEmpty());
        report("failed-catalog", run, outcome);
    }

    @Test
    void INT_H06_secondLiveRunForSameDestinationAndCycleIsRefusedWithoutClaim() throws Exception {
        coordinator.handoff(run);
        UUID second = seed("live", cycle);
        SimulatedHost other = hostFor(DESTINATION, cycle);
        HandoffOutcome outcome = coordinator(other).handoff(second);
        assertEquals(new Held(second, HoldReason.CLAIM_CONFLICT), outcome);
        assertEquals(0, other.mutations);
        assertEquals(0, (int) owner.queryForObject("SELECT count(*) FROM interest.publication WHERE run_id = ?",
                Integer.class, second));
        assertEquals("COMPUTED", runState(second));
        report("claim-conflict", second, outcome, other);
    }

    @Test
    void tamperedStagedRecordRejectsPacket() throws Exception {
        owner.update("UPDATE interest.interest_tx SET amount = amount + 0.01 WHERE run_id = ? AND emit_ordinal = 2", run);
        assertEquals("RECORD_COLUMN_MISMATCH", assertThrows(PacketBuilder.PacketRejected.class,
                () -> coordinator.handoff(run)).reason());
        assertEquals(0, host.hostCalls);
        assertEquals(0, (int) owner.queryForObject("SELECT count(*) FROM interest.publication WHERE run_id = ?",
                Integer.class, run));
    }

    @Test
    void ordinalGapRejectsPacket() throws Exception {
        owner.update("UPDATE interest.interest_tx SET emit_ordinal = 3 WHERE run_id = ? AND emit_ordinal = 2", run);
        assertEquals("ORDINAL_GAP", assertThrows(PacketBuilder.PacketRejected.class,
                () -> new PacketBuilder(publisher).build(run)).reason());
    }

    @Test
    void stalePreimageFingerprintRejectsPacket() throws Exception {
        owner.update("UPDATE interest.account_delta SET source_fingerprint = sha256('x'::bytea) WHERE run_id = ?", run);
        assertEquals("DELTA_PREIMAGE", assertThrows(PacketBuilder.PacketRejected.class,
                () -> new PacketBuilder(publisher).build(run)).reason());
    }

    @Test
    void runNotComputedRejectsPacket() throws Exception {
        owner.update("UPDATE interest.run SET state = 'COMPUTING' WHERE run_id = ?", run);
        assertEquals("RUN_NOT_COMPUTED", assertThrows(PacketBuilder.PacketRejected.class,
                () -> coordinator.handoff(run)).reason());
        assertEquals(0, host.hostCalls);
    }

    private void assertHeldWithoutMutation(HoldReason reason, String label) throws Exception {
        HandoffOutcome outcome = coordinator.handoff(run);
        assertEquals(new Held(run, reason), outcome);
        assertEquals(0, host.mutations);
        assertTrue(host.generations.isEmpty());
        assertArrayEquals(Digests.sha256(fixtureRecords("input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt", 300).get(0)),
                new PacketBuilder(publisher).build(run).deltas().get(0).preimageFingerprint());
        assertHeld(reason);
        report(label, run, outcome);
    }

    private void assertHeld(HoldReason reason) {
        assertEquals("HELD", publicationState(run));
        assertEquals("HELD", runState(run));
        assertEquals(reason.name(), owner.queryForObject(
                "SELECT receipt->>'reason' FROM interest.publication WHERE run_id = ?", String.class, run));
        assertEquals("false", owner.queryForObject(
                "SELECT receipt->>'verified' FROM interest.publication WHERE run_id = ?", String.class, run));
        assertTrue(signals.isEmpty());
    }

    private void report(String label, UUID id, HandoffOutcome outcome) {
        report(label, id, outcome, host);
    }

    private void report(String label, UUID id, HandoffOutcome outcome, SimulatedHost h) {
        System.out.println("FAULT " + label + " outcome=" + outcome.getClass().getSimpleName()
                + (outcome instanceof Held held ? " reason=" + held.reason() : "")
                + " publication=" + publicationState(id) + " run=" + runState(id) + " signals=" + signals.size()
                + " host_calls=" + h.hostCalls + " host_mutations=" + h.mutations + " generations=" + h.generations.size());
    }

    private HandoffCoordinator coordinator(SimulatedHost h) {
        return new HandoffCoordinator(new PacketBuilder(publisher), new PublicationLedger(publisher, publishTx), h,
                (id, generation) -> signals.add(id + "|" + generation));
    }

    private SimulatedHost hostFor(String destination, String forCycle) throws Exception {
        SimulatedHost h = new SimulatedHost(destination, forCycle);
        for (byte[] raw : fixtureRecords("input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt", 300)) {
            h.accounts.put(new String(raw, 0, 11, StandardCharsets.US_ASCII), raw);
        }
        return h;
    }

    private String publicationState(UUID id) {
        return owner.queryForList("SELECT state FROM interest.publication WHERE run_id = ?", String.class, id)
                .stream().findFirst().orElse("NONE");
    }

    private String runState(UUID id) {
        return owner.queryForObject("SELECT state FROM interest.run WHERE run_id = ?", String.class, id);
    }

    /** i01-shaped COMPUTED run: sealed account_input, two interest_tx rows, one account_delta for the updated account. */
    static UUID seed(String mode, String forCycle) throws Exception {
        UUID snapshot = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO interest.snapshot VALUES (?, '{}', 'lab-ascii', 'SEALED', '2022-07-05 10:00:00')", snapshot);
        owner.update("INSERT INTO interest.run VALUES (?,?,'2022071800',?,'SYSTRAN',?,'INT-05','legacy-A','frozen','COMPUTED')",
                id, snapshot, forCycle, mode);
        List<byte[]> before = fixtureRecords("input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt", 300);
        List<byte[]> after = fixtureRecords("expected/datasets/AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.txt", 300);
        for (int i = 0; i < before.size(); i++) {
            byte[] raw = before.get(i);
            FixedRecordCodec.Account a = (FixedRecordCodec.Account) FixedRecordCodec
                    .decode(Layout.ACCOUNT, Profile.LAB_ASCII, raw).fields();
            owner.update("INSERT INTO interest.account_input VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", snapshot,
                    a.acctId(), a.activeStatus(), a.currentBalance(), a.creditLimit(), a.cashCreditLimit(),
                    a.openDate(), a.expirationDate(), a.reissueDate(), a.cycleCredit(), a.cycleDebit(), a.addrZip(),
                    a.groupId(), a.filler().array(), raw);
            if (!java.util.Arrays.equals(raw, after.get(i))) {
                FixedRecordCodec.Account n = (FixedRecordCodec.Account) FixedRecordCodec
                        .decode(Layout.ACCOUNT, Profile.LAB_ASCII, after.get(i)).fields();
                owner.update("INSERT INTO interest.account_delta VALUES (?,?,?,?,?,?,?,?,?)", id, a.acctId(),
                        a.currentBalance(), n.currentBalance(), a.cycleCredit(), n.cycleCredit(), a.cycleDebit(),
                        n.cycleDebit(), Digests.sha256(raw));
            }
        }
        long ordinal = 0;
        for (byte[] raw : fixtureRecords("expected/datasets/AWS.M2.CARDDEMO.SYSTRAN.G0001V00.txt", 350)) {
            FixedRecordCodec.InterestTransaction t = (FixedRecordCodec.InterestTransaction) FixedRecordCodec
                    .decode(Layout.TRAN, Profile.LAB_ASCII, raw).fields();
            owner.update("INSERT INTO interest.interest_tx VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", id, ++ordinal,
                    t.txnId(), t.txnType(), t.categoryCode(), t.source(), t.description(), t.amount(), t.merchantId(),
                    t.merchantName(), t.merchantCity(), t.merchantZip(), t.cardNumber(), t.origTs(), t.procTs(),
                    t.filler().array(), raw);
        }
        return id;
    }

    /** Harness text datasets are LF-delimited with trailing spaces trimmed; restore the fixed LRECL. */
    static List<byte[]> fixtureRecords(String relative, int lrecl) throws Exception {
        List<byte[]> out = new ArrayList<>();
        for (String line : Files.readAllLines(I01.resolve(relative), StandardCharsets.US_ASCII)) {
            out.add(String.format("%-" + lrecl + "s", line).getBytes(StandardCharsets.US_ASCII));
        }
        return out;
    }

    static Path locate(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve(relative))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException(relative);
        }
        return dir.resolve(relative);
    }
}
