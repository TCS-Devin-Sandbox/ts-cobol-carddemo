package com.carddemo.interest.pilot;

import static com.carddemo.interest.pilot.PilotFieldCodec.slice;
import static com.carddemo.interest.pilot.PilotFieldCodec.text;
import static com.carddemo.interest.pilot.PilotFieldCodec.unsignedDigits;

/** CARD-XREF-RECORD, CVACT03Y, LRECL 50. */
public record CardXref(String cardNumber, String customerId, String acctId, byte[] filler, byte[] rawRecord) {

    public static final String DATASET = "CARDXREF";
    public static final int LRECL = 50;

    public CardXref {
        filler = filler.clone();
        rawRecord = rawRecord.clone();
    }

    @Override
    public byte[] filler() {
        return filler.clone();
    }

    @Override
    public byte[] rawRecord() {
        return rawRecord.clone();
    }

    public static CardXref decode(byte[] r, long recordNumber) {
        return new CardXref(
                text(r, 0, 16),                                      // XREF-CARD-NUM X(16)
                unsignedDigits(r, 16, 9, DATASET, recordNumber),     // XREF-CUST-ID 9(09)
                unsignedDigits(r, 25, 11, DATASET, recordNumber),    // XREF-ACCT-ID 9(11)
                slice(r, 36, 14),                                    // FILLER X(14)
                r);
    }
}
