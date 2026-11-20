package com.carddemo.interest.calc;

import com.carddemo.interest.codec.FixedRecordCodec.*;
import com.carddemo.interest.job.CloseStore;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

public final class GroupCalculation implements ItemProcessor<AccountGroups.Group, GroupCalculation.Outcome> {
    public record Emission(long ordinal, InterestTransaction transaction) {}
    public record Outcome(AccountGroups.Group group, Decoded account, List<Emission> emissions, BigDecimal total, long ordinal) {
        public Outcome { emissions = List.copyOf(emissions); }
    }
    private final CloseStore store;
    private final UUID snapshot;
    private final UUID run;
    private final InterestRules rules;
    private final String parm;
    private long ordinal;

    public GroupCalculation(CloseStore store, UUID snapshot, UUID run, InterestRules rules, String parm, long ordinal) {
        this.store = store; this.snapshot = snapshot; this.run = run; this.rules = rules; this.parm = parm; this.ordinal = ordinal;
    }

    @Override public Outcome process(AccountGroups.Group group) {
        // INT-H04: every encountered account is eligible, without status or expiry filtering.
        // INT-R09 / INT-R10: lookups precede rate skipping, including a zero-rate first balance.
        Decoded original = store.account(snapshot, group.account()).orElseThrow(() -> new MissingLookup("ACCTFILE"));
        Account account = (Account) original.fields();
        CardXref card = (CardXref) store.card(snapshot, group.account()).orElseThrow(() -> new MissingLookup("XREFFILE")).fields();
        BigDecimal total = new BigDecimal("0.00");
        List<Emission> emissions = new ArrayList<>();
        for (Decoded row : group.balances()) {
            CategoryBalance balance = (CategoryBalance) row.fields();
            BigDecimal rate = rate(balance, account);
            if (!InterestRules.emits(rate)) continue;
            BigDecimal monthly = rules.monthly(balance.balance(), rate);
            total = InterestRules.receiver(total.add(monthly), 11);
            emissions.add(new Emission(++ordinal, rules.transaction(parm, ordinal, account, card, monthly)));
        }
        return new Outcome(group, original, emissions, total, ordinal);
    }

    /** INT-R02 / INT-R03 / INT-H08: exactly one DEFAULT lookup, padded group and unchanged category/type. */
    private BigDecimal rate(CategoryBalance balance, Account account) {
        var exact = store.rate(snapshot, account.groupId(), balance);
        if (exact.isPresent()) return ((DisclosureRate) exact.get().fields()).annualPercentRate();
        LoggerFactory.getLogger(GroupCalculation.class).info("event=rate-fallback run={} dd=DISCGRP op=READ status=23", run);
        return ((DisclosureRate) store.rate(snapshot, "DEFAULT   ", balance)
                .orElseThrow(() -> new MissingLookup("DISCGRP")).fields()).annualPercentRate();
    }

    /** INT-R10 / INT-H08: no identifiers in ordinary exception/log text. */
    public static final class MissingLookup extends LegacyStatus.Failure {
        public MissingLookup(String dd) {
            super(LegacyStatus.Dd.valueOf(dd), LegacyStatus.Operation.READ, "23".getBytes(StandardCharsets.US_ASCII),
                    LegacyStatus.Profile.LAB_ASCII, dd.equals("DISCGRP"), List.of());
        }
    }
}
