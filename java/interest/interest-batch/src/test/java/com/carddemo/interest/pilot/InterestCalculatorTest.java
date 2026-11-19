package com.carddemo.interest.pilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InterestCalculatorTest {

    private final InterestCalculator calculator = new InterestCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void INT_R01_i01_monthlyInterestIsBalanceTimesRateOver1200() {
        assertThat(calculator.monthlyInterest(d("1000.00"), d("12.00"))).isEqualByComparingTo("10.00");
        assertThat(calculator.monthlyInterest(d("2400.00"), d("18.50"))).isEqualByComparingTo("37.00");
    }

    @Test
    void INT_Q01_i02_halfCentTiesTruncateTowardZero() {
        assertThat(calculator.monthlyInterest(d("1235.50"), d("12.00"))).isEqualTo(d("12.35"));
        assertThat(calculator.monthlyInterest(d("1234.50"), d("12.00"))).isEqualTo(d("12.34"));
    }

    @Test
    void INT_Q01_i07_nonTieFractionsTruncateNotRound() {
        // 1000.80 * 12 / 1200 = 10.008 -> 10.00 (rounding would give 10.01)
        assertThat(calculator.monthlyInterest(d("1000.80"), d("12.00"))).isEqualTo(d("10.00"));
    }

    @Test
    void INT_Q01_INT_Q03_negativeFractionTruncatesTowardZeroNotFloor() {
        assertThat(calculator.monthlyInterest(d("-1235.50"), d("12.00"))).isEqualTo(d("-12.35"));
    }

    @Test
    void INT_Q03_i06_negativeBalanceGivesNegativeInterest() {
        assertThat(calculator.monthlyInterest(d("-300.00"), d("12.00"))).isEqualTo(d("-3.00"));
    }

    @Test
    void INT_Q04_tinyBalanceWithNonzeroRateStoresZero() {
        assertThat(calculator.monthlyInterest(d("0.01"), d("12.00"))).isEqualTo(d("0.00"));
    }

    @Test
    void INT_H07_negativeRateIsCalculatedNotExcluded() {
        assertThat(calculator.monthlyInterest(d("1200.00"), d("-1.00"))).isEqualTo(d("-1.00"));
    }

    @Test
    void INT_H05_receiverKeepsLowOrderDigits() {
        // 999999999.99 * 9999.99 / 1200 = 8333324999.9166..; S9(09)V99 keeps 333324999.91.
        assertThat(calculator.monthlyInterest(d("999999999.99"), d("9999.99"))).isEqualTo(d("333324999.91"));
        assertThat(calculator.addToAccountTotal(d("999999999.99"), d("0.02"))).isEqualTo(d("0.01"));
    }

    @Test
    void INT_R06_closingBalanceAddsAccountTotal() {
        assertThat(calculator.closingBalance(d("1000.00"), d("10.00"))).isEqualTo(d("1010.00"));
    }

    @Test
    void INT_R06_accountTotalSumsStoredCentsNotRawQuotients() {
        BigDecimal first = calculator.monthlyInterest(d("1000.80"), d("12.00"));
        BigDecimal second = calculator.monthlyInterest(d("1000.80"), d("12.00"));
        BigDecimal total = calculator.addToAccountTotal(calculator.addToAccountTotal(d("0.00"), first), second);
        assertThat(total).isEqualTo(d("20.00"));
    }
}
