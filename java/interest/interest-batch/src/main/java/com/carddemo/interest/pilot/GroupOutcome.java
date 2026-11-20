package com.carddemo.interest.pilot;

import java.util.List;
import java.util.Optional;

/** Everything one account group produces; persisted atomically by {@link GroupOutcomeWriter}. */
public record GroupOutcome(
        String acctId,
        List<InterestTransaction> transactions,
        Optional<AccountDelta> delta,
        List<SysoutLine> sysout,
        byte[] nextGroupKey,
        long lastOrdinal) {

    public GroupOutcome {
        transactions = List.copyOf(transactions);
        sysout = List.copyOf(sysout);
        nextGroupKey = nextGroupKey == null ? null : nextGroupKey.clone();
    }

    @Override
    public byte[] nextGroupKey() {
        return nextGroupKey == null ? null : nextGroupKey.clone();
    }

    /** One restricted SYSOUT DISPLAY line, numbered in legacy DISPLAY order. */
    public record SysoutLine(long lineNumber, String text) {
    }
}
