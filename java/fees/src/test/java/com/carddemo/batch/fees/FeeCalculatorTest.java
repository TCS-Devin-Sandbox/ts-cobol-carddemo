package com.carddemo.batch.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FeeCalculatorTest {
    @ParameterizedTest
    @CsvSource({
            "STANDARD,1234.56,10,30,30.86,41.15,0",
            "STANDARD,100.00,10,30,5.00,3.33,0",
            "STANDARD,5000.00,10,30,35.00,166.66,0",
            "GOLD,80.00,10,30,0.00,2.66,4",
            "STANDARD,1000.00,10,0,0.00,0.00,8",
            "GOLD,0.00,0,30,0.00,0.00,0",
            "STANDARD,-123.45,10,30,5.00,-4.11,0",
            "STANDARD,100.00,-1,30,0.00,3.33,0",
            "STANDARD,1234.56,0,30,0.00,41.15,0",
            "GOLD,100.00,10,30,5.00,3.33,0",
            "gold,80.00,10,30,5.00,2.66,0",
            "STANDARD,400000000.00,10,30,0.00,13333333.33,8",
            "STANDARD,400000000.00,-1,0,0.00,0.00,8"
    })
    void examplesAndBoundaries(String group, String balance, int lateDays, int cycleDays,
                               String fee, String average, int rc) {
        var result = new FeeCalculator().calculate(request(group, balance, lateDays, cycleDays));
        assertEquals(new BigDecimal(fee), result.FEE_AMT());
        assertEquals(new BigDecimal(average), result.FEE_AVG_DAILY());
        assertEquals(rc, result.FEE_RC());
        assertEquals("00000000001", result.FEE_ACCT_ID());
    }

    @Test
    void blankPaddedGoldAndSizePreservingAverage() {
        var calculator = new FeeCalculator();
        assertEquals(4, calculator.calculate(request("GOLD      ", "80.00", 10, 30)).FEE_RC());
        assertEquals(0, calculator.calculate(request(" GOLD", "80.00", 10, 30)).FEE_RC());
        var overflow = new FeeRequest("00000000001", "STANDARD", new BigDecimal("10000000000.00"),
                10, 1, new BigDecimal("99.00"), new BigDecimal("17.23"), 4);
        var result = calculator.calculate(overflow);
        assertEquals(new BigDecimal("0.00"), result.FEE_AMT());
        assertEquals(new BigDecimal("17.23"), result.FEE_AVG_DAILY());
        assertEquals(8, result.FEE_RC());
    }

    private static FeeRequest request(String group, String balance, int lateDays, int cycleDays) {
        return new FeeRequest("00000000001", group, new BigDecimal(balance), lateDays, cycleDays,
                new BigDecimal("99.00"), new BigDecimal("88.00"), 4);
    }
}
