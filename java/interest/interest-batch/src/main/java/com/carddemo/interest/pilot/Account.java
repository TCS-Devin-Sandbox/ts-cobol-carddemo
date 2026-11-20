package com.carddemo.interest.pilot;

import static com.carddemo.interest.pilot.PilotFieldCodec.signedZoned;
import static com.carddemo.interest.pilot.PilotFieldCodec.slice;
import static com.carddemo.interest.pilot.PilotFieldCodec.text;
import static com.carddemo.interest.pilot.PilotFieldCodec.unsignedDigits;

import java.math.BigDecimal;

/** ACCOUNT-RECORD, CVACT01Y, LRECL 300. Original bytes are kept so untouched fields and filler survive. */
public record Account(
        String acctId,
        String activeStatus,
        BigDecimal currentBalance,
        BigDecimal creditLimit,
        BigDecimal cashCreditLimit,
        String openDate,
        String expirationDate,
        String reissueDate,
        BigDecimal cycleCredit,
        BigDecimal cycleDebit,
        String addrZip,
        String groupId,
        byte[] filler,
        byte[] rawRecord) {

    public static final String DATASET = "ACCTDATA";
    public static final int LRECL = 300;
    static final int CURR_BAL_OFFSET = 12;
    static final int CYC_CREDIT_OFFSET = 78;
    static final int CYC_DEBIT_OFFSET = 90;
    static final int AMOUNT_WIDTH = 12;

    public Account {
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

    public static Account decode(byte[] r, long recordNumber) {
        return new Account(
                unsignedDigits(r, 0, 11, DATASET, recordNumber),             // ACCT-ID 9(11)
                text(r, 11, 1),                                              // ACCT-ACTIVE-STATUS X(01)
                signedZoned(r, CURR_BAL_OFFSET, 12, 2, DATASET, recordNumber), // ACCT-CURR-BAL S9(10)V99
                signedZoned(r, 24, 12, 2, DATASET, recordNumber),            // ACCT-CREDIT-LIMIT S9(10)V99
                signedZoned(r, 36, 12, 2, DATASET, recordNumber),            // ACCT-CASH-CREDIT-LIMIT S9(10)V99
                text(r, 48, 10),                                             // ACCT-OPEN-DATE X(10)
                text(r, 58, 10),                                             // ACCT-EXPIRAION-DATE X(10)
                text(r, 68, 10),                                             // ACCT-REISSUE-DATE X(10)
                signedZoned(r, CYC_CREDIT_OFFSET, 12, 2, DATASET, recordNumber), // ACCT-CURR-CYC-CREDIT
                signedZoned(r, CYC_DEBIT_OFFSET, 12, 2, DATASET, recordNumber),  // ACCT-CURR-CYC-DEBIT
                text(r, 102, 10),                                            // ACCT-ADDR-ZIP X(10)
                text(r, 112, 10),                                            // ACCT-GROUP-ID X(10)
                slice(r, 122, 178),                                          // FILLER X(178)
                r);
    }

    /** REWRITE image: only A03, A09 and A10 change; every other byte is the preimage. */
    public byte[] rewrittenWith(AccountDelta delta) {
        byte[] out = rawRecord.clone();
        put(out, CURR_BAL_OFFSET, delta.newCurrentBalance());
        put(out, CYC_CREDIT_OFFSET, delta.newCycleCredit());
        put(out, CYC_DEBIT_OFFSET, delta.newCycleDebit());
        return out;
    }

    private static void put(byte[] out, int offset, BigDecimal value) {
        byte[] encoded = PilotFieldCodec.encodeSignedZoned(Receiver.ACCOUNT_AMOUNT.store(value), AMOUNT_WIDTH, 2);
        System.arraycopy(encoded, 0, out, offset, AMOUNT_WIDTH);
    }
}
