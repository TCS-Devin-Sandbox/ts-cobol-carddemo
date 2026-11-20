package com.carddemo.interest.pilot;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * COBOL receivers the pilot stores into. Storing truncates the fraction toward zero (no ROUNDED, INT-Q01)
 * and keeps only the low-order integer digits that fit the PICTURE (no ON SIZE ERROR, INT-H05).
 */
enum Receiver {
    /** WS-MONTHLY-INT PIC S9(09)V99 (CBACT04C.cbl:168). */
    MONTHLY_INTEREST(9, 2),
    /** WS-TOTAL-INT PIC S9(09)V99 (CBACT04C.cbl:169). */
    TOTAL_INTEREST(9, 2),
    /** TRAN-AMT PIC S9(09)V99 (CVTRA05Y). */
    TRAN_AMT(9, 2),
    /** ACCT-CURR-BAL / ACCT-CURR-CYC-* PIC S9(10)V99 (CVACT01Y). */
    ACCOUNT_AMOUNT(10, 2);

    private final int integerDigits;
    private final int scale;
    private final BigInteger modulus;

    Receiver(int integerDigits, int scale) {
        this.integerDigits = integerDigits;
        this.scale = scale;
        this.modulus = BigInteger.TEN.pow(integerDigits + scale);
    }

    BigDecimal store(BigDecimal value) {
        BigInteger unscaled = value.setScale(scale, RoundingMode.DOWN).unscaledValue();
        return new BigDecimal(unscaled.remainder(modulus), scale);
    }

    int width() {
        return integerDigits + scale;
    }

    int scale() {
        return scale;
    }
}
