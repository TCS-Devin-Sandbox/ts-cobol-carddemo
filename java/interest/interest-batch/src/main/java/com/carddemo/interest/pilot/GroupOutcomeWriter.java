package com.carddemo.interest.pilot;

import java.util.UUID;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/** Persists group outcomes and the run checkpoint inside the chunk transaction. */
public class GroupOutcomeWriter implements ItemWriter<GroupOutcome> {

    private final PilotStore store;
    private final UUID runId;

    public GroupOutcomeWriter(PilotStore store, UUID runId) {
        this.store = store;
        this.runId = runId;
    }

    @Override
    public void write(Chunk<? extends GroupOutcome> outcomes) {
        for (GroupOutcome outcome : outcomes) {
            store.insertTransactions(runId, outcome.transactions());
            outcome.delta().ifPresent(delta -> store.insertDelta(runId, delta,
                    store.findAccount(runId, delta.acctId()).orElseThrow().rawRecord()));
            store.insertSysout(runId, outcome.sysout());
            store.saveCheckpoint(runId, outcome.nextGroupKey(), outcome.acctId(), outcome.lastOrdinal());
        }
    }
}
