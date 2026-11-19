package com.carddemo.interest.pilot;

import java.util.List;

/**
 * Consecutive TCATBALF records for one account in key order (INT-R09). {@code followedByAnotherAccount}
 * is the account break that triggers the update; the last group has none (INT-Q02).
 */
public record AccountGroup(String acctId, List<CategoryBalance> balances, boolean followedByAnotherAccount,
        byte[] nextGroupKey) {

    public AccountGroup {
        balances = List.copyOf(balances);
        nextGroupKey = nextGroupKey == null ? null : nextGroupKey.clone();
    }

    @Override
    public byte[] nextGroupKey() {
        return nextGroupKey == null ? null : nextGroupKey.clone();
    }
}
