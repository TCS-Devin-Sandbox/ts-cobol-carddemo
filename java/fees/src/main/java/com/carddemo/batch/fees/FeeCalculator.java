package com.carddemo.batch.fees;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FeeCalculator {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final PrintStream sysprint;

    public FeeCalculator() {
        this(System.out);
    }

    public FeeCalculator(PrintStream sysprint) {
        this.sysprint = sysprint;
    }

    public FeeRequest calculate(FeeRequest request) {
        BigDecimal average = request.FEE_AVG_DAILY();
        if (request.FEE_CYCLE_DAYS() == 0) {
            sysprint.println("CBFEECAL: ZERODIVIDE FOR ACCOUNT  " + request.FEE_ACCT_ID());
            return result(request, ZERO, ZERO, 8);
        }
        try {
            average = decimal(request.FEE_CYCLE_BAL().divide(
                    BigDecimal.valueOf(request.FEE_CYCLE_DAYS()), 2, RoundingMode.DOWN), 12);
            if (request.FEE_LATE_DAYS() <= 0) return result(request, ZERO, average, 0);
            if ("GOLD      ".equals(paddedGroup(request.FEE_GROUP_ID()))
                    && request.FEE_CYCLE_BAL().compareTo(new BigDecimal("100.00")) < 0) {
                return result(request, ZERO, average, 4);
            }
            BigDecimal work = request.FEE_CYCLE_BAL().multiply(new BigDecimal("0.025"));
            if (work.setScale(5, RoundingMode.DOWN).precision() > 15) throw new ArithmeticException("SIZE");
            BigDecimal fee = decimal(work, 9);
            fee = fee.max(new BigDecimal("5.00")).min(new BigDecimal("35.00"));
            return result(request, fee, average, 0);
        } catch (ArithmeticException size) {
            sysprint.println("CBFEECAL: SIZE CONDITION FOR ACCOUNT  " + request.FEE_ACCT_ID());
            return result(request, ZERO, average, 8);
        }
    }

    private static BigDecimal decimal(BigDecimal value, int precision) {
        BigDecimal truncated = value.setScale(2, RoundingMode.DOWN);
        if (truncated.precision() > precision) throw new ArithmeticException("SIZE");
        return truncated;
    }

    private static String paddedGroup(String group) {
        if (group.length() > 10) throw new IllegalArgumentException("FEE_GROUP_ID exceeds CHAR(10)");
        return group + " ".repeat(10 - group.length());
    }

    private static FeeRequest result(FeeRequest request, BigDecimal fee, BigDecimal average, int rc) {
        return new FeeRequest(request.FEE_ACCT_ID(), request.FEE_GROUP_ID(), request.FEE_CYCLE_BAL(),
                request.FEE_LATE_DAYS(), request.FEE_CYCLE_DAYS(), fee, average, rc);
    }
}
