package com.carddemo.interest.pilot;

import static com.carddemo.interest.pilot.PilotFieldCodec.signedZoned;
import static com.carddemo.interest.pilot.PilotFieldCodec.slice;
import static com.carddemo.interest.pilot.PilotFieldCodec.text;
import static com.carddemo.interest.pilot.PilotFieldCodec.unsignedDigits;

import java.math.BigDecimal;

/** TRAN-CAT-BAL-RECORD, CVTRA01Y, LRECL 50. {@code legacyKey} is the 17 encoded key bytes (INT-R09 order). */
public record CategoryBalance(
        byte[] legacyKey, String acctId, String txnType, String categoryCode, BigDecimal balance,
        byte[] filler, byte[] rawRecord) {

    public static final String DATASET = "TCATBALF";
    public static final int LRECL = 50;

    public CategoryBalance {
        legacyKey = legacyKey.clone();
        filler = filler.clone();
        rawRecord = rawRecord.clone();
    }

    @Override
    public byte[] legacyKey() {
        return legacyKey.clone();
    }

    @Override
    public byte[] filler() {
        return filler.clone();
    }

    @Override
    public byte[] rawRecord() {
        return rawRecord.clone();
    }

    public static CategoryBalance decode(byte[] r, long recordNumber) {
        return new CategoryBalance(
                slice(r, 0, 17),                                       // TRAN-CAT-KEY
                unsignedDigits(r, 0, 11, DATASET, recordNumber),       // TRANCAT-ACCT-ID 9(11)
                text(r, 11, 2),                                        // TRANCAT-TYPE-CD X(02)
                unsignedDigits(r, 13, 4, DATASET, recordNumber),       // TRANCAT-CD 9(04)
                signedZoned(r, 17, 11, 2, DATASET, recordNumber),      // TRAN-CAT-BAL S9(09)V99
                slice(r, 28, 22),                                      // FILLER X(22)
                r);
    }
}
