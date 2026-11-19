package com.carddemo.interest.pilot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.batch.item.ItemProcessor;

/**
 * Computes one account group. Run-scoped state: the emit ordinal (INT-R08) and the SYSOUT line counter.
 * Lookups read the run's committed snapshot; nothing here writes.
 */
public class InterestGroupProcessor implements ItemProcessor<AccountGroup, GroupOutcome> {

    static final String NOT_FOUND = "23";
    private final PilotStore store;
    private final InterestCalculator calculator;
    private final LegacyTimestamp timestamps;
    private final TransactionIdSource ids;
    private final UUID runId;
    private long lastOrdinal;
    private long sysoutLine = 1;

    public InterestGroupProcessor(PilotStore store, InterestCalculator calculator, LegacyTimestamp timestamps,
            TransactionIdSource ids, UUID runId) {
        this.store = store;
        this.calculator = calculator;
        this.timestamps = timestamps;
        this.ids = ids;
        this.runId = runId;
    }

    @Override
    public GroupOutcome process(AccountGroup group) {
        // INT-R10 (CBACT04C.cbl:372-391): ACCTFILE INVALID KEY abends the run.
        Account account = store.findAccount(runId, group.acctId())
                .orElseThrow(() -> new PilotFailure.RequiredLookupMissing("ACCTFILE", "READ", NOT_FOUND));
        CardXref card = cardFor(group.acctId());
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        List<InterestTransaction> transactions = new ArrayList<>();
        List<GroupOutcome.SysoutLine> sysout = new ArrayList<>();
        for (CategoryBalance balance : group.balances()) {
            sysout.add(line(new String(balance.rawRecord(), PilotFieldCodec.LAB_PROFILE)));
            RateLookup rate = rateFor(account, balance);
            if (rate.fellBackToDefault()) {
                sysout.add(line("DISCLOSURE GROUP RECORD MISSING"));
                sysout.add(line("TRY WITH DEFAULT GROUP CODE"));
            }
            // INT-R04: a zero rate is skipped. INT-H07 (OPEN): negative rates are not excluded.
            if (rate.annualRate().signum() == 0) {
                continue;
            }
            BigDecimal monthly = calculator.monthlyInterest(balance.balance(), rate.annualRate());
            total = calculator.addToAccountTotal(total, monthly);
            // INT-R05 / INT-Q04 (OPEN): every nonzero-rate balance emits a row, even a 0.00 amount.
            // INT-H01 (OPEN): 1400-COMPUTE-FEES is an empty paragraph; no fee path exists.
            lastOrdinal++;
            transactions.add(InterestTransaction.interestCharge(lastOrdinal, ids.idFor(lastOrdinal), account, card,
                    monthly, timestamps.sample()));
        }
        // INT-Q02 (OPEN, legacy kept): only an account break updates the account; the last one is not.
        // INT-Q06 (OPEN): accounts without TCATBALF records never reach this processor and stay untouched.
        Optional<AccountDelta> delta = group.followedByAnotherAccount()
                ? Optional.of(AccountDelta.closing(account, calculator.closingBalance(account.currentBalance(), total)))
                : Optional.empty();
        return new GroupOutcome(group.acctId(), transactions, delta, sysout, group.nextGroupKey(), lastOrdinal);
    }

    /**
     * INT-H03 (OPEN): XREFFIL1 is a non-unique AIX; one match is used, none abends (INT-R10), and several
     * are held because the host AIX return order is not evidenced.
     */
    private CardXref cardFor(String acctId) {
        List<CardXref> cards = store.findCardsByAccount(runId, acctId);
        if (cards.isEmpty()) {
            throw new PilotFailure.RequiredLookupMissing("XREFFILE", "READ", NOT_FOUND);
        }
        if (cards.size() > 1) {
            throw new PilotFailure.UnprovenHostOutcome("INT-H03", "non-unique AIX match count=" + cards.size());
        }
        return cards.get(0);
    }

    /**
     * INT-R02 exact key (group, type, category); INT-R03 status 23 retries with the padded DEFAULT group;
     * INT-H08 (OPEN): a missing DEFAULT row abends the run.
     */
    private RateLookup rateFor(Account account, CategoryBalance balance) {
        Optional<DisclosureRate> exact = store.findRate(runId, account.groupId(), balance.txnType(),
                balance.categoryCode());
        if (exact.isPresent()) {
            return new RateLookup(exact.get().annualRate(), false);
        }
        DisclosureRate fallback = store.findRate(runId, DisclosureRate.DEFAULT_GROUP, balance.txnType(),
                        balance.categoryCode())
                .orElseThrow(() -> new PilotFailure.RequiredLookupMissing("DISCGRP", "READ", NOT_FOUND));
        return new RateLookup(fallback.annualRate(), true);
    }

    private GroupOutcome.SysoutLine line(String text) {
        return new GroupOutcome.SysoutLine(++sysoutLine, text);
    }

    record RateLookup(BigDecimal annualRate, boolean fellBackToDefault) {
    }
}
