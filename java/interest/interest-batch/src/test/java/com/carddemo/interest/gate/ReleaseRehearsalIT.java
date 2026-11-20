package com.carddemo.interest.gate;

import static org.junit.jupiter.api.Assertions.*;

import com.carddemo.interest.handoff.ExportPacket;
import com.carddemo.interest.handoff.HandoffCoordinator;
import com.carddemo.interest.handoff.HandoffOutcome;
import com.carddemo.interest.handoff.HoldReason;
import com.carddemo.interest.handoff.PacketBuilder;
import com.carddemo.interest.handoff.PublicationLedger;
import com.carddemo.interest.job.CloseExporter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class ReleaseRehearsalIT extends GateSupport {
    UUID run;
    ExportPacket packet;
    GateHost host;
    HandoffCoordinator coordinator;

    void liveCandidate() throws Exception {
        liveCandidate(fixture("i01-baseline").resolve("input"));
    }
    void liveCandidate(Path input) throws Exception {
        var execution = launch(input, temp.resolve("candidate"));
        assertEquals("0", execution.getExitStatus().getExitCode());
        run = runId(execution);
        assertEquals("COMPUTED", state(run));
        assertEquals("PRIVATE", jdbc.queryForObject("SELECT state FROM interest.publication WHERE run_id=?", String.class, run));
        // Owner-only lab conversion: discard the shadow claim before the isolated live rehearsal.
        jdbc.update("DELETE FROM interest.publication WHERE run_id=? AND state='PRIVATE'", run);
        jdbc.update("UPDATE interest.run SET mode='live',cycle=? WHERE run_id=?", "gate-" + UUID.randomUUID(), run);
        packet = new PacketBuilder(jdbc).build(run);
        host = new GateHost(packet.destination(), packet.cycle());
        for (byte[] raw : rows(input.resolve("AWS.M2.CARDDEMO.ACCTDATA.PS.txt"), 300))
            host.accounts.put(new String(raw, 0, 11, StandardCharsets.US_ASCII), raw);
        coordinator = new HandoffCoordinator(new PacketBuilder(jdbc),
                new PublicationLedger(jdbc, new TransactionTemplate(manager)), host, (id, generation) -> host.signals++);
    }
    void held(HandoffOutcome outcome, HoldReason reason) {
        assertEquals(new HandoffOutcome.Held(run, reason), outcome);
        assertEquals("HELD", state(run));
        assertEquals("HELD", jdbc.queryForObject("SELECT state FROM interest.publication WHERE run_id=?", String.class, run));
        assertEquals(reason.name(), jdbc.queryForObject("SELECT receipt->>'reason' FROM interest.publication WHERE run_id=?", String.class, run));
        assertEquals(0, host.signals);
    }
    void report(String scenario) {
        System.out.printf("REHEARSAL scenario=%s run_state=%s applies=%d catalogs=%d generations=%d quarantined=%d restored=%d signals=%d%n",
                scenario, state(run), host.applyCalls, host.catalogCalls, host.generations.size(), host.quarantine.size(), host.restored, host.signals);
    }
    @AfterEach void sqlInvariants() { invariants(); }

    @Test void INT_R10_unstagedSnapshotHasNoPacket() throws Exception {
        var execution = launch(specimen("malformed-tcatbalf"), temp);
        run = runId(execution);
        assertEquals("12", execution.getExitStatus().getExitCode());
        assertEquals(0, count("publication", run));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM interest.run WHERE run_id=? AND state='COMPUTED'", Integer.class, run));
        assertEquals("RUN_MISSING", assertThrows(PacketBuilder.PacketRejected.class, () -> new PacketBuilder(jdbc).build(run)).reason());
        UUID snapshot = UUID.fromString(execution.getExecutionContext().getString("snapshot"));
        assertTrue(jdbc.queryForObject("SELECT state<>'SEALED' FROM interest.snapshot WHERE snapshot_id=?", Boolean.class, snapshot));
        System.out.println("REHEARSAL scenario=unstaged rc=12 computed=0 publications=0 packet=RUN_MISSING");
    }
    @Test void INT_H06_stalePreimageHoldsBeforeMutation() throws Exception {
        liveCandidate();
        String account = packet.deltas().getFirst().acctId();
        host.accounts.get(account)[299] = 'X';
        held(coordinator.handoff(run), HoldReason.PREIMAGE_MISMATCH);
        assertEquals(0, host.applyCalls);
        assertEquals(0, host.catalogCalls);
        assertTrue(host.generations.isEmpty());
        report("stale-preimage");
    }
    @Test void INT_H06_changedFenceHoldsBeforeMutation() throws Exception {
        liveCandidate();
        host.changeFenceAfterPreflight = true;
        held(coordinator.handoff(run), HoldReason.FENCE_STALE);
        assertEquals(0, host.applyCalls);
        assertEquals(0, host.catalogCalls);
        report("changed-fence");
    }
    @Test void INT_H06_unknownApplyAndMissingReceiptNeverReapply() throws Exception {
        liveCandidate();
        host.unknownApply = true;
        held(coordinator.handoff(run), HoldReason.APPLY_UNCERTAIN);
        assertEquals(1, host.applyCalls);
        assertEquals(1, host.beforeJournal.size());
        assertEquals(0, host.catalogCalls);
        held(coordinator.resolve(run), HoldReason.RECEIPT_MISSING);
        held(coordinator.handoff(run), HoldReason.RECEIPT_MISSING);
        assertEquals(1, host.applyCalls);
        assertEquals(0, host.catalogCalls);
        report("unknown-apply-no-receipt");
    }
    @Test void INT_H06_manualRestoreOnlyJournaledChangesAndQuarantinesExactGeneration() throws Exception {
        liveCandidate();
        host.ambiguousCatalog = true;
        held(coordinator.handoff(run), HoldReason.CATALOG_AMBIGUOUS);
        assertEquals(1, host.beforeJournal.size());
        assertEquals(1, host.generations.size());
        String generation = host.generations.keySet().iterator().next();
        byte[] payload = host.generations.get(generation).clone();
        host.restoreJournalAndQuarantine(generation);
        assertEquals(packet.deltas().size(), host.restored);
        assertEquals(1, host.quarantine.size());
        bytes("restore quarantined generation record=0", payload, host.quarantine.get(generation));
        for (byte[] raw : rows(fixture("i01-baseline").resolve(INPUT_ACCOUNT), 300)) {
            String account = account(raw).acctId();
            bytes("restore capture record=" + account, raw, host.accounts.get(account));
        }
        assertEquals(rows(fixture("i01-baseline").resolve(INPUT_ACCOUNT), 300).size(), host.accounts.size());
        assertEquals(GateHost.NO_GENERATION, host.currentGeneration(packet.destination()));
        held(coordinator.resolve(run), HoldReason.RECEIPT_MISSING);
        held(coordinator.handoff(run), HoldReason.RECEIPT_MISSING);
        assertEquals(1, host.applyCalls);
        assertEquals(1, host.catalogCalls);
        assertTrue(host.generations.isEmpty());
        report("manual-restoration");
    }
    @Test void INT_H06_partialAccountApplyRestoresOnlyJournaledSubset() throws Exception {
        Path input = temp.resolve("combined-input");
        Files.createDirectories(input);
        for (var dataset : Map.of("ACCTDATA", 300, "CARDXREF", 50, "TCATBALF", 50, "DISCGRP", 50).entrySet()) {
            String filename = "AWS.M2.CARDDEMO." + dataset.getKey() + ".PS.txt";
            int keyLength = switch (dataset.getKey()) {
                case "ACCTDATA" -> 11;
                case "CARDXREF", "DISCGRP" -> 16;
                default -> 17;
            };
            var merged = new TreeMap<String, byte[]>();
            for (String name : new String[]{"i01-baseline", "i02-tie-half-cent"}) {
                for (byte[] raw : rows(fixture(name).resolve("input").resolve(filename), dataset.getValue())) {
                    String key = new String(raw, 0, keyLength, StandardCharsets.US_ASCII);
                    byte[] prior = merged.putIfAbsent(key, raw);
                    if (prior != null) assertTrue(Arrays.equals(prior, raw), "combined fixture duplicate key conflict");
                }
            }
            CloseExporter.writeFixed(input.resolve(filename), merged.values().stream().toList());
        }
        liveCandidate(input);
        assertEquals(3, packet.deltas().size());
        var capture = new TreeMap<String, byte[]>();
        host.accounts.forEach((key, raw) -> capture.put(key, raw.clone()));
        host.unknownApply = true;
        host.unknownApplyAt = 2;
        held(coordinator.handoff(run), HoldReason.APPLY_UNCERTAIN);
        assertEquals(2, host.beforeJournal.size());
        assertEquals(0, host.catalogCalls);
        assertTrue(host.generations.isEmpty());
        host.restoreJournalAndQuarantine(packet.destination() + ".G0001V00");
        assertEquals(2, host.restored);
        assertEquals(capture.size(), host.accounts.size());
        capture.forEach((key, raw) -> bytes("partial restore capture record=" + key, raw, host.accounts.get(key)));
        held(coordinator.resolve(run), HoldReason.RECEIPT_MISSING);
        assertEquals(2, host.applyCalls);
        assertEquals(0, host.catalogCalls);
        assertEquals(0, host.quarantine.size(), "no generation had been cataloged to quarantine");
        report("partial-apply-journal-restoration");
    }
    @Test void INT_H06_postConsumptionContainsWithoutRepublishOrAutomaticRerun() throws Exception {
        liveCandidate();
        assertInstanceOf(HandoffOutcome.Released.class, coordinator.handoff(run));
        assertEquals("RELEASED", state(run));
        assertEquals("true", jdbc.queryForObject("SELECT receipt->>'verified' FROM interest.publication WHERE run_id=?", String.class, run));
        host.consumed = true;
        int executions = jdbc.queryForObject("SELECT count(*) FROM interest_batch.batch_job_execution", Integer.class);
        assertInstanceOf(HandoffOutcome.Released.class, coordinator.handoff(run));
        assertInstanceOf(HandoffOutcome.Released.class, coordinator.resolve(run));
        assertThrows(IllegalStateException.class, () -> host.restoreJournalAndQuarantine(host.currentGeneration(packet.destination())));
        assertEquals(1, host.applyCalls);
        assertEquals(1, host.catalogCalls);
        assertEquals(1, host.generations.size());
        assertEquals(1, host.signals);
        assertEquals(executions, jdbc.queryForObject("SELECT count(*) FROM interest_batch.batch_job_execution", Integer.class));
        report("post-consumption-no-replay");
    }
}
