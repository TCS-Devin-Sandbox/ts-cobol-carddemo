package com.carddemo.interest.handoff;

import com.carddemo.interest.handoff.HostBridge.ApplyStatus;
import com.carddemo.interest.handoff.HostBridge.CatalogStatus;
import com.carddemo.interest.handoff.HostBridge.Fence;
import com.carddemo.interest.handoff.HostBridge.Receipt;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fail-closed handoff of a COMPUTED run (INT-ARCH A2.4, A3 Publish row, A4), outside compute and any retrying chunk.
 * Shadow never reaches the host. Live: claim, observe fence, preflight every preimage and TRAN-ID before any
 * mutation, apply guarded patches, catalog exactly one SYSTRAN(+1), then release only on a verified matching receipt.
 * Every other path holds (INT-H06 OPEN: no automatic retry, restore or second generation).
 */
public final class HandoffCoordinator {

    private static final Logger log = LoggerFactory.getLogger(HandoffCoordinator.class);
    private static final Pattern GENERATION = Pattern.compile("\\.G(\\d{4})V00$");
    static final String SHADOW_GENERATION = "NOT-PUBLISHED";

    private final PacketBuilder packets;
    private final PublicationLedger ledger;
    private final HostBridge host;
    private final ReleaseSignal signal;

    public HandoffCoordinator(PacketBuilder packets, PublicationLedger ledger, HostBridge host, ReleaseSignal signal) {
        this.packets = packets;
        this.ledger = ledger;
        this.host = host;
        this.signal = signal;
    }

    public HandoffOutcome handoff(UUID runId) {
        Optional<PublicationLedger.Publication> existing = ledger.find(runId);
        if (existing.isPresent()) {
            return revisit(existing.get());
        }
        ExportPacket packet = packets.build(runId);
        String operationId = operationId(packet);
        if (packet.shadow()) {
            ledger.claim(packet, SHADOW_GENERATION, SHADOW_GENERATION, operationId, preconditions(packet));
            log.info("event=handoff-shadow run={} records={} deltas={}", runId, packet.recordCount(), packet.deltas().size());
            return new HandoffOutcome.ShadowRehearsed(runId, packet.payloadDigestHex());
        }
        String prior = host.currentGeneration(packet.destination());
        String next = nextGeneration(packet.destination(), prior);
        PublicationLedger.Claim claim = ledger.claim(packet, prior, next, operationId, preconditions(packet));
        if (claim instanceof PublicationLedger.Claim.Conflict) {
            log.info("event=handoff-refused run={} reason={}", runId, HoldReason.CLAIM_CONFLICT);
            return new HandoffOutcome.Held(runId, HoldReason.CLAIM_CONFLICT);
        }
        if (claim instanceof PublicationLedger.Claim.Existing e) {
            return revisit(e.publication());
        }
        return publish(packet, prior, next, operationId);
    }

    /** A held run is released only by a verified matching receipt; nothing is reapplied. */
    public HandoffOutcome resolve(UUID runId) {
        PublicationLedger.Publication publication = ledger.find(runId)
                .orElseThrow(() -> new IllegalStateException("no publication for run"));
        if (publication.state() == PublicationLedger.State.RECEIVED) {
            return new HandoffOutcome.Released(runId, publication.currentGeneration(), true);
        }
        if (publication.state() == PublicationLedger.State.PRIVATE) {
            return new HandoffOutcome.ShadowRehearsed(runId, packets.build(runId).payloadDigestHex());
        }
        ExportPacket packet = packets.build(runId);
        return verifyAndRelease(packet, publication.currentGeneration(), publication.operationId());
    }

    private HandoffOutcome revisit(PublicationLedger.Publication publication) {
        return switch (publication.state()) {
            case RECEIVED -> new HandoffOutcome.Released(publication.runId(), publication.currentGeneration(), true);
            case PRIVATE -> new HandoffOutcome.ShadowRehearsed(publication.runId(),
                    packets.build(publication.runId()).payloadDigestHex());
            case PREPARED, HELD -> resolve(publication.runId());
        };
    }

    private HandoffOutcome publish(ExportPacket packet, String prior, String next, String operationId) {
        UUID runId = packet.runId();
        Optional<Fence> observed = host.observeFence(packet.destination());
        if (observed.isEmpty()) {
            return hold(runId, HoldReason.FENCE_MISSING);
        }
        Fence fence = observed.get();
        if (!current(fence, packet)) {
            return hold(runId, HoldReason.FENCE_STALE);
        }
        for (ExportPacket.GuardedDelta d : packet.deltas()) {
            Optional<byte[]> record;
            try {
                record = host.readAccount(fence, d.acctId());
            } catch (RuntimeException e) {
                record = Optional.empty();
            }
            if (record.isEmpty() || !Arrays.equals(Digests.sha256(record.get()), d.preimageFingerprint())) {
                return hold(runId, HoldReason.PREIMAGE_MISMATCH);
            }
        }
        Set<String> seen = new HashSet<>(host.existingTransactionIds(packet.destination()));
        for (ExportPacket.PayloadRecord r : packet.records()) {
            if (!seen.add(r.txnId())) {
                return hold(runId, HoldReason.TXN_ID_COLLISION);
            }
        }
        for (ExportPacket.GuardedDelta d : packet.deltas()) {
            if (!stillFenced(fence, packet)) {
                return hold(runId, HoldReason.FENCE_STALE);
            }
            ApplyStatus applied;
            try {
                applied = host.applyAccountPatch(fence, operationId, d.acctId(), d.preimageFingerprint(), d.postImage());
            } catch (RuntimeException e) {
                applied = ApplyStatus.UNKNOWN;
            }
            if (applied != ApplyStatus.APPLIED) {
                return hold(runId, HoldReason.APPLY_UNCERTAIN);
            }
        }
        if (!stillFenced(fence, packet)) {
            return hold(runId, HoldReason.FENCE_STALE);
        }
        if (!host.currentGeneration(packet.destination()).equals(prior)) {
            return hold(runId, HoldReason.GENERATION_MOVED);
        }
        CatalogStatus cataloged;
        try {
            cataloged = host.writeAndCatalog(fence, operationId, next, packet.payload());
        } catch (RuntimeException e) {
            cataloged = CatalogStatus.UNKNOWN;
        }
        if (cataloged != CatalogStatus.CATALOGED) {
            return hold(runId, HoldReason.CATALOG_AMBIGUOUS);
        }
        return verifyAndRelease(packet, next, operationId);
    }

    private HandoffOutcome verifyAndRelease(ExportPacket packet, String next, String operationId) {
        UUID runId = packet.runId();
        Optional<Receipt> receipt;
        try {
            receipt = host.receipt(operationId);
        } catch (RuntimeException e) {
            receipt = Optional.empty();
        }
        if (receipt.isEmpty()) {
            return hold(runId, HoldReason.RECEIPT_MISSING);
        }
        if (!matches(receipt.get(), packet, next, operationId)) {
            return hold(runId, HoldReason.RECEIPT_MISMATCH);
        }
        if (ledger.release(runId, receiptJson(receipt.get()))) {
            signal.intcalcCompleted(runId, next);
            log.info("event=handoff-released run={} records={} deltas={}", runId, packet.recordCount(),
                    packet.deltas().size());
            return new HandoffOutcome.Released(runId, next, false);
        }
        return new HandoffOutcome.Released(runId, next, true);
    }

    static boolean matches(Receipt r, ExportPacket packet, String next, String operationId) {
        Map<String, String> expected = packet.deltas().stream().collect(Collectors.toMap(
                ExportPacket.GuardedDelta::acctId, d -> HexFormat.of().formatHex(d.postImageFingerprint())));
        return r.operationId().equals(operationId) && r.generation().equals(next)
                && r.payloadSha256().equals(packet.payloadDigestHex()) && r.recordCount() == packet.recordCount()
                && r.accountPostImageSha256().equals(expected) && r.generationsCataloged().equals(List.of(next));
    }

    private boolean current(Fence fence, ExportPacket packet) {
        return fence.active() && fence.destination().equals(packet.destination()) && fence.cycle().equals(packet.cycle());
    }

    private boolean stillFenced(Fence fence, ExportPacket packet) {
        return host.observeFence(packet.destination()).filter(f -> f.equals(fence) && current(f, packet)).isPresent();
    }

    private HandoffOutcome hold(UUID runId, HoldReason reason) {
        ledger.hold(runId, reason);
        log.info("event=handoff-held run={} reason={}", runId, reason);
        return new HandoffOutcome.Held(runId, reason);
    }

    /** GDG(+1) relative to the observed (0); the first generation of a fresh base is G0001V00 (J 37-41). */
    static String nextGeneration(String destination, String prior) {
        if (prior.equals(HostBridge.NO_GENERATION)) {
            return destination + ".G0001V00";
        }
        Matcher m = GENERATION.matcher(prior);
        if (!prior.startsWith(destination + ".") || !m.find()) {
            throw new IllegalStateException("unexpected generation name");
        }
        int number = Integer.parseInt(m.group(1)) % 9999 + 1;
        return destination + ".G%04dV00".formatted(number);
    }

    /** Deterministic host operation ID: run plus payload digest, so a repeat names the same external effect. */
    static String operationId(ExportPacket packet) {
        byte[] id = Digests.sha256((packet.runId() + "|" + packet.payloadDigestHex()).getBytes());
        return "INT05-" + HexFormat.of().formatHex(id, 0, 12);
    }

    private static String preconditions(ExportPacket packet) {
        HexFormat hex = HexFormat.of();
        return packet.deltas().stream().map(d -> "{\"acct_id\":\"" + d.acctId() + "\",\"preimage_sha256\":\""
                + hex.formatHex(d.preimageFingerprint()) + "\",\"postimage_sha256\":\""
                + hex.formatHex(d.postImageFingerprint()) + "\"}").collect(Collectors.joining(",", "[", "]"));
    }

    private static String receiptJson(Receipt r) {
        String accounts = r.accountPostImageSha256().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"").collect(Collectors.joining(",", "{", "}"));
        return "{\"verified\":true,\"operation_id\":\"" + r.operationId() + "\",\"generation\":\"" + r.generation()
                + "\",\"payload_sha256\":\"" + r.payloadSha256() + "\",\"record_count\":" + r.recordCount()
                + ",\"accounts\":" + accounts + "}";
    }
}
