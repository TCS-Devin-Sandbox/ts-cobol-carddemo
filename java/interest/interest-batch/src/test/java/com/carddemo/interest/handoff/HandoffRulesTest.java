package com.carddemo.interest.handoff;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HandoffRulesTest {
    static final String DEST = "AWS.M2.CARDDEMO.SYSTRAN";

    @Test
    void firstGenerationOfFreshBaseIsG0001V00() {
        assertEquals(DEST + ".G0001V00", HandoffCoordinator.nextGeneration(DEST, HostBridge.NO_GENERATION));
    }

    @Test
    void nextGenerationIsRelativePlusOneOfObservedCurrent() {
        assertEquals(DEST + ".G0008V00", HandoffCoordinator.nextGeneration(DEST, DEST + ".G0007V00"));
        assertEquals(DEST + ".G0001V00", HandoffCoordinator.nextGeneration(DEST, DEST + ".G9999V00"));
    }

    @Test
    void unexpectedGenerationNameIsNotGuessed() {
        assertThrows(IllegalStateException.class, () -> HandoffCoordinator.nextGeneration(DEST, "OTHER.G0001V00"));
        assertThrows(IllegalStateException.class, () -> HandoffCoordinator.nextGeneration(DEST, DEST + ".X"));
    }

    @Test
    void operationIdIsDeterministicPerRunAndPayload() {
        ExportPacket a = packet(UUID.fromString("00000000-0000-0000-0000-000000000005"), (byte) ' ');
        ExportPacket b = packet(a.runId(), (byte) ' ');
        ExportPacket c = packet(a.runId(), (byte) '1');
        assertEquals(HandoffCoordinator.operationId(a), HandoffCoordinator.operationId(b));
        assertNotEquals(HandoffCoordinator.operationId(a), HandoffCoordinator.operationId(c));
        assertTrue(HandoffCoordinator.operationId(a).matches("INT05-[0-9a-f]{24}"));
    }

    @Test
    void receiptMustMatchEveryPacketFact() {
        ExportPacket p = packet(UUID.randomUUID(), (byte) ' ');
        String op = HandoffCoordinator.operationId(p);
        String gen = DEST + ".G0001V00";
        HostBridge.Receipt ok = new HostBridge.Receipt(op, gen, p.payloadDigestHex(), 1, Map.of(), List.of(gen));
        assertTrue(HandoffCoordinator.matches(ok, p, gen, op));
        assertFalse(HandoffCoordinator.matches(new HostBridge.Receipt("other", gen, p.payloadDigestHex(), 1, Map.of(),
                List.of(gen)), p, gen, op));
        assertFalse(HandoffCoordinator.matches(new HostBridge.Receipt(op, gen, p.payloadDigestHex(), 2, Map.of(),
                List.of(gen)), p, gen, op));
        assertFalse(HandoffCoordinator.matches(new HostBridge.Receipt(op, gen, p.payloadDigestHex(), 1,
                Map.of("00000002001", "00"), List.of(gen)), p, gen, op));
        assertFalse(HandoffCoordinator.matches(new HostBridge.Receipt(op, gen, p.payloadDigestHex(), 1, Map.of(),
                List.of(gen, DEST + ".G0002V00")), p, gen, op));
    }

    @Test
    void payloadRecordRejectsWrongGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new ExportPacket.PayloadRecord(1, "x", new byte[349]));
    }

    @Test
    void packetIsImmutable() {
        ExportPacket p = packet(UUID.randomUUID(), (byte) ' ');
        p.payloadDigest()[0] ^= 1;
        p.records().get(0).raw()[0] = 'Z';
        assertArrayEquals(Digests.sha256(p.payload()), p.payloadDigest());
        assertThrows(UnsupportedOperationException.class, () -> p.records().clear());
    }

    private static ExportPacket packet(UUID run, byte fill) {
        byte[] raw = new byte[350];
        java.util.Arrays.fill(raw, fill);
        return new ExportPacket(run, "live", DEST, "2022-07", "SYSTRAN",
                List.of(new ExportPacket.PayloadRecord(1, "2022071800000001", raw)),
                List.<ExportPacket.GuardedDelta>of(), Digests.sha256(raw), "{}");
    }

}
