package com.carddemo.interest.pilot;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure calculation; no I/O and no state. */
public final class InterestCalculator {

    private static final BigDecimal MONTHS_TIMES_PERCENT = BigDecimal.valueOf(1200);

    /**
     * INT-R01 (CBACT04C.cbl:464-465): {@code (TRAN-CAT-BAL * DIS-INT-RATE) / 1200}. The product is exact;
     * the stored cent truncates toward zero (INT-Q01 OPEN, legacy kept), negative balances give negative
     * interest (INT-Q03 OPEN, legacy kept) and the receiver keeps low-order digits (INT-H05 OPEN).
     */
    public BigDecimal monthlyInterest(BigDecimal balance, BigDecimal annualRate) {
        BigDecimal product = balance.multiply(annualRate);
        BigDecimal truncated = product.divide(MONTHS_TIMES_PERCENT, Receiver.MONTHLY_INTEREST.scale(), RoundingMode.DOWN);
        return Receiver.MONTHLY_INTEREST.store(truncated);
    }

    /** INT-R06 (CBACT04C.cbl:467): ADD WS-MONTHLY-INT TO WS-TOTAL-INT. */
    public BigDecimal addToAccountTotal(BigDecimal total, BigDecimal monthlyInterest) {
        return Receiver.TOTAL_INTEREST.store(total.add(monthlyInterest));
    }

    /** INT-R06 (CBACT04C.cbl:352): ADD WS-TOTAL-INT TO ACCT-CURR-BAL. */
    public BigDecimal closingBalance(BigDecimal currentBalance, BigDecimal totalInterest) {
        return Receiver.ACCOUNT_AMOUNT.store(currentBalance.add(totalInterest));
    }
}
