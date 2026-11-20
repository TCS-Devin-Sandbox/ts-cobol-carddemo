package com.carddemo.interest.pilot;

import static com.carddemo.interest.pilot.PilotFieldCodec.fixedText;

import java.math.BigDecimal;

/** TRAN-RECORD, CVTRA05Y, LRECL 350, as written by 1300-B-WRITE-TX (CBACT04C.cbl:473-500). */
public record InterestTransaction(
        long emitOrdinal,
        String txnId,
        String txnType,
        String categoryCode,
        String source,
        String description,
        BigDecimal amount,
        String merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String cardNumber,
        String origTs,
        String procTs,
        byte[] filler) {

    public static final int LRECL = 350;
    /** Pilot assumption: the 20-byte FILLER is written as spaces (the legacy area is never assigned). */
    static final byte[] SPACE_FILLER = fixedText("", 20);

    public InterestTransaction {
        filler = filler.clone();
    }

    @Override
    public byte[] filler() {
        return filler.clone();
    }

    /**
     * INT-R05 constants: type '01', category '05' into PIC 9(04) (= 0005), source 'System', description
     * STRING 'Int. for a/c ' ACCT-ID, merchant id 0 and merchant text spaces; card from XREF-CARD-NUM.
     */
    public static InterestTransaction interestCharge(long emitOrdinal, String txnId, Account account,
            CardXref card, BigDecimal monthlyInterest, String timestamp) {
        return new InterestTransaction(emitOrdinal, txnId, "01", "0005", "System    ",
                "Int. for a/c " + account.acctId(),
                Receiver.TRAN_AMT.store(monthlyInterest),
                "000000000", "", "", "", card.cardNumber(), timestamp, timestamp, SPACE_FILLER);
    }

    public byte[] toRecord() {
        byte[] out = new byte[LRECL];
        int at = 0;
        at = put(out, at, fixedText(txnId, 16));
        at = put(out, at, fixedText(txnType, 2));
        at = put(out, at, fixedText(categoryCode, 4));
        at = put(out, at, fixedText(source, 10));
        at = put(out, at, fixedText(description, 100));
        at = put(out, at, PilotFieldCodec.encodeSignedZoned(amount, 11, 2));
        at = put(out, at, fixedText(merchantId, 9));
        at = put(out, at, fixedText(merchantName, 50));
        at = put(out, at, fixedText(merchantCity, 50));
        at = put(out, at, fixedText(merchantZip, 10));
        at = put(out, at, fixedText(cardNumber, 16));
        at = put(out, at, fixedText(origTs, 26));
        at = put(out, at, fixedText(procTs, 26));
        put(out, at, filler);
        return out;
    }

    private static int put(byte[] out, int at, byte[] field) {
        System.arraycopy(field, 0, out, at, field.length);
        return at + field.length;
    }
}
