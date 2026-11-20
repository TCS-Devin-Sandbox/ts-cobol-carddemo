package com.carddemo.interest.pilot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;

/**
 * INT-R09: walks the TCATBALF snapshot in encoded key order and hands out one group per account break,
 * looking ahead one record so the group knows whether another account follows (INT-Q02).
 */
public class AccountGroupReader implements ItemStreamReader<AccountGroup> {

    private final PilotStore store;
    private final UUID runId;
    private List<CategoryBalance> balances = List.of();
    private int next;

    public AccountGroupReader(PilotStore store, UUID runId) {
        this.store = store;
        this.runId = runId;
    }

    @Override
    public void open(ExecutionContext executionContext) {
        balances = store.categoryBalancesInKeyOrder(runId);
        next = 0;
    }

    @Override
    public AccountGroup read() {
        if (next >= balances.size()) {
            return null;
        }
        String acctId = balances.get(next).acctId();
        List<CategoryBalance> group = new ArrayList<>();
        while (next < balances.size() && balances.get(next).acctId().equals(acctId)) {
            group.add(balances.get(next++));
        }
        boolean anotherAccountFollows = next < balances.size();
        return new AccountGroup(acctId, group, anotherAccountFollows,
                anotherAccountFollows ? balances.get(next).legacyKey() : null);
    }
}
