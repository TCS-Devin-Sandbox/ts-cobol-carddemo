package com.carddemo.interest.pilot;

/**
 * INT-R08 (CBACT04C.cbl:474-479): TRAN-ID = PARM-DATE (opaque 10 bytes, INT-H02 OPEN) followed by
 * WS-TRANID-SUFFIX PIC 9(06), which keeps its low six digits when it passes 999999 (INT-H05 OPEN).
 */
public final class TransactionIdSource {

    private static final long SUFFIX_MODULUS = 1_000_000L;
    private final String parm;

    public TransactionIdSource(String parm) {
        if (parm == null || parm.length() != 10) {
            throw new PilotFailure.UnprovenHostOutcome("INT-H02",
                    "PARM must be exactly the 10 bytes of PARM-DATE; shorter PARMs read unassigned linkage bytes");
        }
        this.parm = parm;
    }

    public String idFor(long emitOrdinal) {
        return parm + String.format("%06d", emitOrdinal % SUFFIX_MODULUS);
    }
}
