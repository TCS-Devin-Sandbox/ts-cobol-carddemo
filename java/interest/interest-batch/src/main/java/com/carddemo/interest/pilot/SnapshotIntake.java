package com.carddemo.interest.pilot;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Reads the real fixture inputs into the run's private snapshot. Any unreadable record fails the step
 * and the snapshot transaction rolls back (INT-R10).
 */
public class SnapshotIntake implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(SnapshotIntake.class);
    private final PilotStore store;
    private final UUID runId;
    private final Path inputDir;

    public SnapshotIntake(PilotStore store, UUID runId, Path inputDir) {
        this.store = store;
        this.runId = runId;
        this.inputDir = inputDir;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<Account> accounts = decode(Account.DATASET, Account.LRECL, Account::decode);
        List<CardXref> cards = decode(CardXref.DATASET, CardXref.LRECL, CardXref::decode);
        List<CategoryBalance> balances = decode(CategoryBalance.DATASET, CategoryBalance.LRECL, CategoryBalance::decode);
        List<DisclosureRate> rates = decode(DisclosureRate.DATASET, DisclosureRate.LRECL, DisclosureRate::decode);
        store.insertAccounts(runId, accounts);
        store.insertCardXrefs(runId, cards);
        store.insertCategoryBalances(runId, balances);
        store.insertDisclosureRates(runId, rates);
        contribution.incrementReadCount();
        log.info("event=intake run={} accounts={} xrefs={} balances={} rates={}",
                runId, accounts.size(), cards.size(), balances.size(), rates.size());
        return RepeatStatus.FINISHED;
    }

    private <T> List<T> decode(String dataset, int lrecl, BiFunction<byte[], Long, T> decoder) {
        List<byte[]> records = FixtureDatasets.readFixed(FixtureDatasets.input(inputDir, dataset), dataset, lrecl);
        return java.util.stream.IntStream.range(0, records.size())
                .mapToObj(i -> decoder.apply(records.get(i), (long) i + 1))
                .toList();
    }
}
