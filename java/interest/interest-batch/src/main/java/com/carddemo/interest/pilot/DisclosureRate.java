package com.carddemo.interest.pilot;

import static com.carddemo.interest.pilot.PilotFieldCodec.signedZoned;
import static com.carddemo.interest.pilot.PilotFieldCodec.slice;
import static com.carddemo.interest.pilot.PilotFieldCodec.text;
import static com.carddemo.interest.pilot.PilotFieldCodec.unsignedDigits;

import java.math.BigDecimal;

/** DIS-GROUP-RECORD, CVTRA02Y, LRECL 50. */
public record DisclosureRate(
        byte[] legacyKey, String groupId, String txnType, String categoryCode, BigDecimal annualRate,
        byte[] filler, byte[] rawRecord) {

    public static final String DATASET = "DISCGRP";
    public static final int LRECL = 50;
    /** INT-R03: MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID pads the X(10) key with spaces. */
    public static final String DEFAULT_GROUP = "DEFAULT   ";

    public DisclosureRate {
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

    public static DisclosureRate decode(byte[] r, long recordNumber) {
        return new DisclosureRate(
                slice(r, 0, 16),                                     // DIS-GROUP-KEY
                text(r, 0, 10),                                      // DIS-ACCT-GROUP-ID X(10)
                text(r, 10, 2),                                      // DIS-TRAN-TYPE-CD X(02)
                unsignedDigits(r, 12, 4, DATASET, recordNumber),     // DIS-TRAN-CAT-CD 9(04)
                signedZoned(r, 16, 6, 2, DATASET, recordNumber),     // DIS-INT-RATE S9(04)V99
                slice(r, 22, 28),                                    // FILLER X(28)
                r);
    }
}
