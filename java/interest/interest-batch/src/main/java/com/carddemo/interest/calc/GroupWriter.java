package com.carddemo.interest.calc;

import com.carddemo.interest.codec.FixedRecordCodec.Account;
import com.carddemo.interest.job.CloseStore;
import java.util.UUID;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** S05/S13: chunk size ONE; rows, eligible delta and next-key checkpoint share the PostgreSQL commit. */
public final class GroupWriter implements ItemWriter<GroupCalculation.Outcome> {
    public interface CommitHook {
        default void beforeCommit(UUID run, long ordinal) {}
        default void afterCommit(UUID run, long ordinal) {}
    }
    private final CloseStore store;
    private final UUID run;
    private final CommitHook hook;
    public GroupWriter(CloseStore store, UUID run, CommitHook hook) { this.store = store; this.run = run; this.hook = hook; }

    @Override public void write(Chunk<? extends GroupCalculation.Outcome> chunk) {
        for (var outcome : chunk) {
            outcome.emissions().forEach(e -> store.transaction(run, e.ordinal(), e.transaction()));
            if (outcome.group().normalBreak()) {
                Account account = (Account) outcome.account().fields();
                store.delta(run, outcome.account(), InterestRules.receiver(account.currentBalance().add(outcome.total()), 12));
            }
            store.checkpoint(run, outcome.group().nextKey() == null ? null : outcome.group().nextKey().array(),
                    outcome.group().account(), outcome.ordinal());
            hook.beforeCommit(run, outcome.ordinal());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { hook.afterCommit(run, outcome.ordinal()); }
            });
        }
    }
}
