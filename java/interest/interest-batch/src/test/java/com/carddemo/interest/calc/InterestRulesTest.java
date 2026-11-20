package com.carddemo.interest.calc;

import static org.junit.jupiter.api.Assertions.*;
import com.carddemo.interest.codec.FixedRecordCodec;
import com.carddemo.interest.codec.FixedRecordCodec.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterestRulesTest {
    private final InterestRules rules = new InterestRules(Clock.fixed(Instant.parse("2022-07-05T10:00:00.129999Z"), ZoneOffset.UTC));
    private static BigDecimal d(String n) { return new BigDecimal(n); }

    @Test void INT_R01_INT_Q01_tiesAndI07NonTiesTruncateBeforeSumming() {
        assertEquals(d("12.35"), rules.monthly(d("1235.50"), d("12.00")));
        assertEquals(d("12.34"), rules.monthly(d("1234.50"), d("12.00")));
        assertEquals(d("13.61"), rules.monthly(d("777.77"), d("21.00")));
    }

    @Test void INT_Q03_INT_H07_negativeSubCentUsesDownNotFloorAndNegativeRateIsNotFiltered() {
        assertEquals(d("0.00"), rules.monthly(d("-0.50"), d("12.00")));
        assertEquals(d("-3.00"), rules.monthly(d("300.00"), d("-12.00")));
        assertTrue(InterestRules.emits(d("-12.00")));
    }

    @Test void INT_R04_INT_Q04_nonzeroRateEmitsZeroCentsAndExactZeroRateSkips() {
        assertTrue(InterestRules.emits(d("0.01")));
        assertEquals(d("0.00"), rules.monthly(d("0.01"), d("0.01")));
        assertFalse(InterestRules.emits(d("0.00")));
    }

    @Test void INT_H05_receiverWidthsKeepSignedLowDigitsAtEveryAssignmentAndAddition() {
        assertEquals(d("0.01"), InterestRules.receiver(d("1000000000.01"), 11));
        assertEquals(d("-0.01"), InterestRules.receiver(d("-1000000000.01"), 11));
        assertEquals(d("0.00"), InterestRules.receiver(d("999999999.99").add(d("0.01")), 11));
        assertEquals(d("0.01"), InterestRules.receiver(d("10000000000.01"), 12));
        assertEquals(d("999999999.98"), rules.monthly(d("999999999.99"), d("2400.00")));
    }

    @Test void INT_R08_INT_H05_suffixWrapDoesNotLimitOrdinal() {
        assertEquals("2022071800999999", InterestRules.transactionId("2022071800", 999999));
        assertEquals("2022071800000000", InterestRules.transactionId("2022071800", 1000000));
        assertEquals("2022071800000001", InterestRules.transactionId("2022071800", 1000001));
    }

    @Test void INT_H02_opaqueTenPrintableBytesOnly() {
        assertDoesNotThrow(() -> InterestRules.validateParm("abcdefghij"));
        for (String parm : List.of("", "202207180", "20220718000", "202207180é", "202207180\n"))
            assertThrows(IllegalArgumentException.class, () -> InterestRules.validateParm(parm));
    }

    @Test void INT_Q02_INT_Q06_lookaheadNotEofControlsDeltaEligibility() {
        assertFalse(new AccountGroups.Group("00000000001", List.of(), null).normalBreak());
        assertTrue(new AccountGroups.Group("00000000001", List.of(), new Bytes(new byte[17])).normalBreak());
    }

    @Test void INT_R05_INT_H01_INT_Q05_TN_06_constantsNoFeesAndOneTimestampSample() {
        var account = new Account("00000000001", "N", d("0.00"), d("0.00"), d("0.00"), " ".repeat(10), " ".repeat(10), " ".repeat(10),
                d("0.00"), d("0.00"), " ".repeat(10), " ".repeat(10), new Bytes(new byte[178]));
        var card = new CardXref("4000000000000001", "000000001", account.acctId(), new Bytes(new byte[14]));
        var tx = rules.transaction("2022071800", 1, account, card, d("0.00"));
        assertEquals("2022-07-05-10.00.00.120000", tx.origTs());
        assertEquals(tx.origTs(), tx.procTs());
        assertEquals("01", tx.txnType()); assertEquals("0005", tx.categoryCode());
        assertEquals("System    ", tx.source()); assertEquals("Int. for a/c 00000000001" + " ".repeat(76), tx.description());
        assertEquals(350, FixedRecordCodec.encode(Layout.TRAN, Profile.LAB_ASCII, tx).length);
        assertArrayEquals(" ".repeat(20).getBytes(java.nio.charset.StandardCharsets.US_ASCII), tx.filler().array());
    }
}
