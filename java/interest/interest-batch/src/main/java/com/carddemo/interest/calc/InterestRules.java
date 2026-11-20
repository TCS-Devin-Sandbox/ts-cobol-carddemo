package com.carddemo.interest.calc;

import com.carddemo.interest.codec.FixedRecordCodec;
import com.carddemo.interest.codec.FixedRecordCodec.Account;
import com.carddemo.interest.codec.FixedRecordCodec.Bytes;
import com.carddemo.interest.codec.FixedRecordCodec.CardXref;
import com.carddemo.interest.codec.FixedRecordCodec.InterestTransaction;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

public final class InterestRules {
    private final Clock clock;

    public InterestRules(Clock clock) { this.clock = clock; }

    /** INT-R01 / INT-Q01 / INT-Q03 / INT-H07: exact product, truncate toward zero, including negatives. */
    public BigDecimal monthly(BigDecimal balance, BigDecimal rate) {
        return receiver(balance.multiply(rate).divide(new BigDecimal("1200"), 2, RoundingMode.DOWN), 11);
    }

    /** INT-H05: each DISPLAY assignment keeps low digits and the signed receiver's sign. */
    public static BigDecimal receiver(BigDecimal value, int precision) {
        return new BigDecimal(value.setScale(2, RoundingMode.UNNECESSARY).unscaledValue()
                .remainder(BigInteger.TEN.pow(precision)), 2);
    }

    /** INT-R04 / INT-Q04: only the exact rate controls skipping, not the computed cents. */
    public static boolean emits(BigDecimal rate) { return rate.signum() != 0; }

    /** INT-H02: opaque printable LAB_ASCII bytes, not a parsed business date. */
    public static void validateParm(String parm) {
        if (parm == null || parm.length() != 10 || parm.chars().anyMatch(c -> c < 32 || c > 126))
            throw new IllegalArgumentException("INT-H02_PARM_HELD");
    }

    /** INT-R08 / INT-H05: the six-digit receiver wraps independently of the staged ordinal. */
    public static String transactionId(String parm, long ordinal) {
        validateParm(parm);
        return parm + String.format(Locale.ROOT, "%06d", ordinal % 1_000_000);
    }

    /** INT-Q05 / TN-02: one local sample for both timestamp fields, hundredths then four zeros. */
    public String timestamp() {
        LocalDateTime t = LocalDateTime.now(clock);
        return String.format(Locale.ROOT, "%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }

    /** INT-R05 / INT-H01 / TN-06: no fee path; lab-observed unused tail and filler are spaces. */
    public InterestTransaction transaction(String parm, long ordinal, Account account, CardXref card, BigDecimal amount) {
        String time = timestamp();
        return new InterestTransaction(transactionId(parm, ordinal), "01", "0005", "System    ",
                pad("Int. for a/c " + account.acctId(), 100), receiver(amount, 11), "000000000",
                " ".repeat(50), " ".repeat(50), " ".repeat(10), card.cardNumber(), time, time,
                new Bytes(" ".repeat(20).getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }

    private static String pad(String value, int width) { return value + " ".repeat(width - value.length()); }
}
