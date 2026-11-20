package com.carddemo.interest.calc;

import static com.carddemo.interest.calc.LegacyStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyStatusTest {
    private static byte[] ascii(String status) { return status.getBytes(StandardCharsets.US_ASCII); }

    @Test void TN_04_INT_R10_numericStatusUsesExactFourPositionMove() {
        assertEquals("FILE STATUS IS: NNNN0023", format(ascii("23"), Profile.LAB_ASCII));
        assertEquals("FILE STATUS IS: NNNN0010", format(ascii("10"), Profile.LAB_ASCII));
        assertEquals("FILE STATUS IS: NNNN0030", format(ascii("30"), Profile.LAB_ASCII));
        assertEquals("FILE STATUS IS: NNNN0000", format(ascii("00"), Profile.LAB_ASCII));
    }

    @Test void TN_04_INT_R10_extendedStatusReinterpretsSecondStorageByteNotItsDigitValue() {
        assertEquals("FILE STATUS IS: NNNN9057", format(ascii("99"), Profile.LAB_ASCII));
        assertEquals("FILE STATUS IS: NNNN9065", format(ascii("9A"), Profile.LAB_ASCII));
        assertEquals("FILE STATUS IS: NNNNA049", format(ascii("A1"), Profile.LAB_ASCII));
        assertEquals("FILE STATUS IS: NNNN9255", format(new byte[]{'9', (byte) 255}, Profile.LAB_ASCII));
        assertEquals("FILE STATUS IS: NNNNA002", format(new byte[]{'A', 2}, Profile.LAB_ASCII));
    }

    @Test void TN_04_INT_R10_profileControlsCharacterAndRightByteOverlayWithoutClaimingHostLayout() {
        // CBACT04C 638–642: second byte C1 is 193, not ASCII A=65; little-endian 00 41 is 16640 -> PIC 999=640.
        assertEquals("FILE STATUS IS: NNNN9193", format(new byte[]{(byte) 0xf9, (byte) 0xc1}, Profile.SYNTHETIC_CP037));
        assertEquals("FILE STATUS IS: NNNN0023", format(new byte[]{(byte) 0xf2, (byte) 0xf3}, Profile.SYNTHETIC_CP037));
        assertEquals("FILE STATUS IS: NNNN9640", format(ascii("9A"), Profile.SYNTHETIC_LITTLE_ENDIAN_ASCII));
    }

    @Test void TN_03_TN_09_INT_R10_everySupportedOperationUsesItsCobolDisplayText() {
        record Probe(Dd dd, Operation operation, boolean defaultRead, String expected) {}
        var probes = List.of(
                new Probe(Dd.TCATBALF, Operation.OPEN, false, "ERROR OPENING TRANSACTION CATEGORY BALANCE"),
                new Probe(Dd.XREFFILE, Operation.OPEN, false, "ERROR OPENING CROSS REF FILE30"),
                new Probe(Dd.DISCGRP, Operation.OPEN, false, "ERROR OPENING DALY REJECTS FILE"),
                new Probe(Dd.ACCTFILE, Operation.OPEN, false, "ERROR OPENING ACCOUNT MASTER FILE"),
                new Probe(Dd.TRANSACT, Operation.OPEN, false, "ERROR OPENING TRANSACTION FILE"),
                new Probe(Dd.TCATBALF, Operation.READ, false, "ERROR READING TRANSACTION CATEGORY FILE"),
                new Probe(Dd.XREFFILE, Operation.READ, false, "ERROR READING XREF FILE"),
                new Probe(Dd.DISCGRP, Operation.READ, false, "ERROR READING DISCLOSURE GROUP FILE"),
                new Probe(Dd.DISCGRP, Operation.READ, true, "ERROR READING DEFAULT DISCLOSURE GROUP"),
                new Probe(Dd.ACCTFILE, Operation.READ, false, "ERROR READING ACCOUNT FILE"),
                new Probe(Dd.TRANSACT, Operation.WRITE, false, "ERROR WRITING TRANSACTION RECORD"),
                new Probe(Dd.ACCTFILE, Operation.REWRITE, false, "ERROR RE-WRITING ACCOUNT FILE"),
                new Probe(Dd.TCATBALF, Operation.CLOSE, false, "ERROR CLOSING TRANSACTION BALANCE FILE"),
                new Probe(Dd.XREFFILE, Operation.CLOSE, false, "ERROR CLOSING CROSS REF FILE"),
                new Probe(Dd.DISCGRP, Operation.CLOSE, false, "ERROR CLOSING DISCLOSURE GROUP FILE"),
                new Probe(Dd.ACCTFILE, Operation.CLOSE, false, "ERROR CLOSING ACCOUNT FILE"),
                new Probe(Dd.TRANSACT, Operation.CLOSE, false, "ERROR CLOSING TRANSACTION FILE"));
        for (var probe : probes) {
            var failure = assertThrows(Failure.class, () -> check(probe.dd(), probe.operation(), ascii("30"),
                    Profile.LAB_ASCII, probe.defaultRead(), List.of()));
            assertEquals(List.of(probe.expected(), "FILE STATUS IS: NNNN0030", "ABENDING PROGRAM",
                    "CEE3ABD: USER ABEND U+000000999"), failure.diagnostics(), probe.toString());
        }
    }

    @Test void INT_R10_INT_H08_TN_07_onlySequentialTcatbalfReadHasEofAndOnlyExactRateMissFallsBack() {
        assertEquals(Outcome.SUCCESS, check(Dd.TRANSACT, Operation.WRITE, ascii("00"), Profile.LAB_ASCII, false, List.of()));
        assertEquals(Outcome.END_OF_INPUT, check(Dd.TCATBALF, Operation.READ, ascii("10"), Profile.LAB_ASCII, false, List.of()));
        assertEquals(Outcome.DEFAULT_RATE, check(Dd.DISCGRP, Operation.READ, ascii("23"), Profile.LAB_ASCII, false, List.of()));
        for (var dd : Dd.values()) {
            for (var operation : Operation.values()) {
                if (dd == Dd.TCATBALF && operation == Operation.READ) continue;
                assertThrows(Failure.class, () -> check(dd, operation, ascii("10"), Profile.LAB_ASCII, false, List.of()));
            }
        }
        var missing = new GroupCalculation.MissingLookup("DISCGRP");
        assertEquals(List.of("ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM",
                "CEE3ABD: USER ABEND U+000000999"), missing.diagnostics());
        assertThrows(Failure.class, () -> check(Dd.DISCGRP, Operation.READ, ascii("23"), Profile.LAB_ASCII, true, List.of()));
    }

    @Test void TN_04_INT_R10_invalidGeometryRejectedAndFailureProtectsStatusAndRestrictedPrefix() {
        for (var status : List.of(new byte[0], new byte[1], new byte[3])) {
            assertThrows(IllegalArgumentException.class, () -> format(status, Profile.LAB_ASCII));
            assertThrows(IllegalArgumentException.class, () -> check(Dd.ACCTFILE, Operation.READ, status, Profile.LAB_ASCII, false, List.of()));
        }
        byte[] status = ascii("23");
        var prefix = new java.util.ArrayList<>(List.of("restricted synthetic DISPLAY"));
        var failure = new Failure(Dd.ACCTFILE, Operation.READ, status, Profile.LAB_ASCII, false, prefix);
        status[0] = '9'; prefix.clear();
        assertEquals("FILE STATUS IS: NNNN0023", failure.diagnostics().get(1));
        assertEquals(List.of("restricted synthetic DISPLAY"), failure.restrictedPrefix());
        assertFalse(failure.getMessage().contains("restricted synthetic DISPLAY"));
        assertThrows(UnsupportedOperationException.class, () -> failure.restrictedPrefix().clear());
    }
}
