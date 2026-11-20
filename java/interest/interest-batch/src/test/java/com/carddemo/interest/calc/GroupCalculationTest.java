package com.carddemo.interest.calc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.carddemo.interest.codec.FixedRecordCodec;
import com.carddemo.interest.codec.FixedRecordCodec.*;
import com.carddemo.interest.job.CloseStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupCalculationTest {
    private static final UUID SNAPSHOT = UUID.randomUUID();
    private static final UUID RUN = UUID.randomUUID();
    private static final String ACCOUNT = "00000000001";
    private static final String GROUP = "TEST      ";
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final CloseStore store = mock(CloseStore.class);
    private final Clock clock = Clock.fixed(Instant.parse("2022-07-05T10:00:00Z"), ZoneOffset.UTC);

    private static Decoded decoded(Layout layout, BusinessRecord row) {
        return FixedRecordCodec.decode(layout, Profile.LAB_ASCII, FixedRecordCodec.encode(layout, Profile.LAB_ASCII, row));
    }
    private static Decoded balance(String account, String category) {
        return decoded(Layout.TCATBAL, new CategoryBalance(account, "01", category, new BigDecimal("999999999.99"), new Bytes(new byte[22])));
    }
    private void lookups(BigDecimal rate) {
        var account = new Account(ACCOUNT, "Y", ZERO, ZERO, ZERO, " ".repeat(10), " ".repeat(10), " ".repeat(10),
                ZERO, ZERO, " ".repeat(10), GROUP, new Bytes(new byte[178]));
        var card = new CardXref("4000000000000001", "000000001", ACCOUNT, new Bytes(new byte[14]));
        when(store.account(SNAPSHOT, ACCOUNT)).thenReturn(Optional.of(decoded(Layout.ACCOUNT, account)));
        when(store.card(SNAPSHOT, ACCOUNT)).thenReturn(Optional.of(decoded(Layout.XREF, card)));
        when(store.rate(eq(SNAPSHOT), eq(GROUP), any())).thenReturn(Optional.of(decoded(Layout.DISCGRP,
                new DisclosureRate(GROUP, "01", "0001", rate, new Bytes(new byte[28])))));
    }

    @Test void INT_H05_INT_R08_overflowKeepsEveryRowAndWrapsSuffixAndGroupTotal() {
        lookups(new BigDecimal("2400.00"));
        var group = new AccountGroups.Group(ACCOUNT, List.of(balance(ACCOUNT, "0001"), balance(ACCOUNT, "0002")), null);
        var processor = new GroupCalculation(store, SNAPSHOT, RUN, new InterestRules(clock), "2022071800", 999998);
        var result = processor.process(group);
        assertEquals(2, result.emissions().size());
        assertEquals(1000000, result.ordinal());
        assertEquals("2022071800999999", result.emissions().get(0).transaction().txnId());
        assertEquals("2022071800000000", result.emissions().get(1).transaction().txnId());
        assertEquals(new BigDecimal("999999999.98"), result.emissions().get(0).transaction().amount());
        assertEquals(new BigDecimal("999999999.98"), result.emissions().get(1).transaction().amount());
        assertEquals(new BigDecimal("999999999.96"), result.total());
    }

    @Test void INT_R09_INT_Q02_lookaheadNextKeyAndResumeKeepFinalGroupWithoutFinalDelta() {
        var a = balance(ACCOUNT, "0001");
        var b = balance(ACCOUNT, "0002");
        var c = balance("00000000002", "0001");
        when(store.rows(SNAPSHOT, Layout.TCATBAL)).thenReturn(List.of(a, b, c));
        var reader = new AccountGroups(store, SNAPSHOT, new CloseStore.Checkpoint(false, null, 0));
        var first = reader.read();
        assertEquals(2, first.balances().size()); assertTrue(first.normalBreak());
        assertArrayEquals(c.legacyKey(), first.nextKey().array());
        var last = reader.read(); assertFalse(last.normalBreak()); assertEquals(1, last.balances().size());
        assertNull(reader.read());
        var resumed = new AccountGroups(store, SNAPSHOT, new CloseStore.Checkpoint(true, first.nextKey(), 2));
        assertEquals("00000000002", resumed.read().account()); assertNull(resumed.read());
        assertNull(new AccountGroups(store, SNAPSHOT, new CloseStore.Checkpoint(true, null, 3)).read());
    }

    @Test void INT_Q06_INT_R10_emptyTcatbalfIsNormalEofWithoutLookups() {
        when(store.rows(SNAPSHOT, Layout.TCATBAL)).thenReturn(List.of());
        assertNull(new AccountGroups(store, SNAPSHOT, new CloseStore.Checkpoint(false, null, 0)).read());
        verify(store, never()).account(any(), anyString());
        verify(store, never()).card(any(), anyString());
    }

    @Test void INT_R04_INT_R10_zeroRateDoesNotBypassRequiredAccountOrCardReads() {
        var group = new AccountGroups.Group(ACCOUNT, List.of(balance(ACCOUNT, "0001")), null);
        when(store.account(SNAPSHOT, ACCOUNT)).thenReturn(Optional.empty());
        var processor = new GroupCalculation(store, SNAPSHOT, RUN, new InterestRules(clock), "2022071800", 0);
        assertEquals("ACCTFILE", assertThrows(GroupCalculation.MissingLookup.class, () -> processor.process(group)).dd());
        lookups(ZERO);
        when(store.card(SNAPSHOT, ACCOUNT)).thenReturn(Optional.empty());
        assertEquals("XREFFILE", assertThrows(GroupCalculation.MissingLookup.class, () -> processor.process(group)).dd());
        verify(store, never()).rate(any(), anyString(), any());
    }

    @Test void INT_Q05_TN_02_advancingClockSamplesOncePerEmittedRow() {
        lookups(new BigDecimal("12.00"));
        class AdvancingClock extends Clock {
            int samples;
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.parse("2022-07-05T10:00:00.129999Z").plusSeconds(samples++); }
        }
        var advancing = new AdvancingClock();
        var processor = new GroupCalculation(store, SNAPSHOT, RUN, new InterestRules(advancing), "2022071800", 0);
        var result = processor.process(new AccountGroups.Group(ACCOUNT, List.of(balance(ACCOUNT, "0001"), balance(ACCOUNT, "0002")), null));
        assertEquals(2, advancing.samples);
        assertEquals("2022-07-05-10.00.00.120000", result.emissions().get(0).transaction().origTs());
        assertEquals("2022-07-05-10.00.01.120000", result.emissions().get(1).transaction().origTs());
        result.emissions().forEach(row -> assertEquals(row.transaction().origTs(), row.transaction().procTs()));
    }
}
