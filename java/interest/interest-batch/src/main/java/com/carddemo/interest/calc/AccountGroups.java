package com.carddemo.interest.calc;

import com.carddemo.interest.codec.FixedRecordCodec;
import com.carddemo.interest.codec.FixedRecordCodec.*;
import com.carddemo.interest.job.CloseStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.batch.item.ItemReader;

/** INT-R09 / A3: ordered byte-key seek from the private business checkpoint, never a Spring offset. */
public final class AccountGroups implements ItemReader<AccountGroups.Group> {
    public record Group(String account, List<Decoded> balances, Bytes nextKey) {
        public Group { balances = List.copyOf(balances); }
        /** INT-Q02: EOF is not a normal account break. INT-Q06: no-balance accounts never form groups. */
        public boolean normalBreak() { return nextKey != null; }
    }
    private final List<Decoded> balances;
    private int index;

    public AccountGroups(CloseStore store, UUID snapshot, CloseStore.Checkpoint checkpoint) {
        this.balances = checkpoint.present() && checkpoint.next() == null ? List.of()
                : store.rows(snapshot, Layout.TCATBAL).stream().filter(r -> checkpoint.next() == null
                || FixedRecordCodec.compareKeys(r.legacyKey(), checkpoint.next().array()) >= 0).toList();
    }

    @Override public Group read() {
        if (index == balances.size()) return null;
        String account = ((CategoryBalance) balances.get(index).fields()).acctId();
        List<Decoded> group = new ArrayList<>();
        do { group.add(balances.get(index++)); }
        while (index < balances.size() && ((CategoryBalance) balances.get(index).fields()).acctId().equals(account));
        return new Group(account, group, index == balances.size() ? null : new Bytes(balances.get(index).legacyKey()));
    }
}
