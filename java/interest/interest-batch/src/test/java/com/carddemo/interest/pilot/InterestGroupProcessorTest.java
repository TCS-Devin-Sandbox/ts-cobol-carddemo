package com.carddemo.interest.pilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterestGroupProcessorTest {

    private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private PilotStore store;
    private InterestGroupProcessor processor;

    private static byte[] padded(String text, int lrecl) {
        byte[] out = new byte[lrecl];
        Arrays.fill(out, (byte) ' ');
        byte[] src = text.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }

    private static Account account(String id, String balance, String group) {
        return Account.decode(padded(id + "Y" + balance + "00000050000{00000025000{2019-03-152026-12-312026-12-31"
                + "00000001000{00000002000{10115     " + group, 300), 1);
    }

    private static CategoryBalance balance(String acct, String type, String cat, String zoned) {
        return CategoryBalance.decode(padded(acct + type + cat + zoned, 50), 1);
    }

    private static DisclosureRate rate(String group, String type, String cat, String zoned) {
        return DisclosureRate.decode(padded(group + type + cat + zoned, 50), 1);
    }

    private static CardXref card(String acct) {
        return CardXref.decode(padded("4000000000000001" + "000000001" + acct, 50), 1);
    }

    @BeforeEach
    void setUp() {
        store = mock(PilotStore.class);
        Clock clock = Clock.fixed(Instant.parse("2022-07-05T10:00:00Z"), ZoneOffset.UTC);
        processor = new InterestGroupProcessor(store, new InterestCalculator(), new LegacyTimestamp(clock),
                new TransactionIdSource("2022071800"), RUN);
        when(store.findCardsByAccount(eq(RUN), anyString())).thenAnswer(i -> List.of(card(i.getArgument(1))));
        when(store.findRate(eq(RUN), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
    }

    private void givenAccount(Account a) {
        when(store.findAccount(RUN, a.acctId())).thenReturn(Optional.of(a));
    }

    private void givenRate(DisclosureRate r) {
        when(store.findRate(RUN, r.groupId(), r.txnType(), r.categoryCode())).thenReturn(Optional.of(r));
    }

    @Test
    void INT_R06_INT_R07_accountBreakProducesDeltaWithZeroCycleTotals() {
        givenAccount(account("00000002001", "00000010000{", "GOLD      "));
        givenRate(rate("GOLD      ", "01", "0001", "00120{"));
        GroupOutcome out = processor.process(new AccountGroup("00000002001",
                List.of(balance("00000002001", "01", "0001", "0000010000{")), true, new byte[17]));
        assertThat(out.transactions()).singleElement().satisfies(t -> {
            assertThat(t.txnId()).isEqualTo("2022071800000001");
            assertThat(t.amount()).isEqualTo(new BigDecimal("10.00"));
        });
        AccountDelta delta = out.delta().orElseThrow();
        assertThat(delta.newCurrentBalance()).isEqualTo(new BigDecimal("1010.00"));
        assertThat(delta.newCycleCredit()).isEqualByComparingTo("0");
        assertThat(delta.newCycleDebit()).isEqualByComparingTo("0");
    }

    @Test
    void INT_Q02_finalAccountAtEofHasNoDelta() {
        givenAccount(account("00000002002", "00000024000{", "STANDARD  "));
        givenRate(rate("STANDARD  ", "01", "0001", "00185{"));
        GroupOutcome out = processor.process(new AccountGroup("00000002002",
                List.of(balance("00000002002", "01", "0001", "0000024000{")), false, null));
        assertThat(out.transactions()).singleElement()
                .satisfies(t -> assertThat(t.amount()).isEqualTo(new BigDecimal("37.00")));
        assertThat(out.delta()).isEmpty();
    }

    @Test
    void INT_R04_i03_zeroRateIsSkippedWithoutTransaction() {
        givenAccount(account("00000002001", "00000010000{", "GOLD      "));
        givenRate(rate("GOLD      ", "02", "0001", "00000{"));
        GroupOutcome out = processor.process(new AccountGroup("00000002001",
                List.of(balance("00000002001", "02", "0001", "0000010000{")), true, new byte[17]));
        assertThat(out.transactions()).isEmpty();
        assertThat(out.delta().orElseThrow().newCurrentBalance()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(out.sysout()).hasSize(1);
    }

    @Test
    void INT_Q04_nonzeroRateWithZeroAmountStillEmitsRow() {
        givenAccount(account("00000002001", "00000010000{", "GOLD      "));
        givenRate(rate("GOLD      ", "01", "0001", "00120{"));
        GroupOutcome out = processor.process(new AccountGroup("00000002001",
                List.of(balance("00000002001", "01", "0001", "0000000000A")), false, null));
        assertThat(out.transactions()).singleElement()
                .satisfies(t -> assertThat(t.amount()).isEqualTo(new BigDecimal("0.00")));
    }

    @Test
    void INT_R03_i04_missingGroupFallsBackToPaddedDefaultWithSysoutLines() {
        givenAccount(account("00000002001", "00000010000{", "PLATINUM  "));
        givenRate(rate("DEFAULT   ", "01", "0001", "00210{"));
        GroupOutcome out = processor.process(new AccountGroup("00000002001",
                List.of(balance("00000002001", "01", "0001", "0000010000{")), false, null));
        assertThat(out.transactions()).singleElement()
                .satisfies(t -> assertThat(t.amount()).isEqualTo(new BigDecimal("17.50")));
        assertThat(out.sysout()).extracting(GroupOutcome.SysoutLine::text).containsExactly(
                "000000020010100010000010000{                      ",
                "DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE");
    }

    @Test
    void INT_H08_missingDefaultRateFailsRun() {
        givenAccount(account("00000002001", "00000010000{", "PLATINUM  "));
        assertThatThrownBy(() -> processor.process(new AccountGroup("00000002001",
                List.of(balance("00000002001", "01", "0001", "0000010000{")), false, null)))
                .isInstanceOfSatisfying(PilotFailure.RequiredLookupMissing.class,
                        f -> assertThat(f.dd()).isEqualTo("DISCGRP"));
    }

    @Test
    void INT_R10_missingAccountFailsRun() {
        when(store.findAccount(any(), anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> processor.process(new AccountGroup("00000002001",
                List.of(balance("00000002001", "01", "0001", "0000010000{")), false, null)))
                .isInstanceOfSatisfying(PilotFailure.RequiredLookupMissing.class,
                        f -> assertThat(f.dd()).isEqualTo("ACCTFILE"));
    }

    @Test
    void INT_H03_nonUniqueAixMatchIsHeld() {
        givenAccount(account("00000002001", "00000010000{", "GOLD      "));
        when(store.findCardsByAccount(RUN, "00000002001")).thenReturn(List.of(card("00000002001"), card("00000002001")));
        assertThatThrownBy(() -> processor.process(new AccountGroup("00000002001",
                List.of(balance("00000002001", "01", "0001", "0000010000{")), false, null)))
                .isInstanceOf(PilotFailure.UnprovenHostOutcome.class);
    }

    @Test
    void INT_R05_INT_R08_multiCategoryEmitsOneRowEachWithRunWideOrdinal() {
        givenAccount(account("00000002001", "00000010000{", "GOLD      "));
        givenRate(rate("GOLD      ", "01", "0001", "00120{"));
        givenRate(rate("GOLD      ", "01", "0002", "00180{"));
        GroupOutcome out = processor.process(new AccountGroup("00000002001", List.of(
                balance("00000002001", "01", "0001", "0000010000{"),
                balance("00000002001", "01", "0002", "0000020000{")), true, new byte[17]));
        assertThat(out.transactions()).extracting(InterestTransaction::txnId)
                .containsExactly("2022071800000001", "2022071800000002");
        assertThat(out.delta().orElseThrow().newCurrentBalance()).isEqualTo(new BigDecimal("1040.00"));
        assertThat(out.lastOrdinal()).isEqualTo(2);
    }
}
