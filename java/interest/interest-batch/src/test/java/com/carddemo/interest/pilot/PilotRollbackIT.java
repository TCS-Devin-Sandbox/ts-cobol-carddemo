package com.carddemo.interest.pilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;

/** Failure paths: the run fails and no pilot output for that run is committed (INT-R10). */
class PilotRollbackIT extends PilotPostgresTestSupport {

    private PilotRunService.PilotRunResult run(String specimen) throws Exception {
        Path out = Path.of("target/pilot-it/" + specimen).toAbsolutePath();
        return service.run(SPECIMENS.resolve(specimen), out);
    }

    private void assertFailedWithNothingCommitted(PilotRunService.PilotRunResult r, Class<?> failure, String specimen) {
        assertThat(r.status()).isEqualTo(BatchStatus.FAILED);
        assertThat(r.exitCode()).isEqualTo(PilotRunService.FAILED_RC);
        assertThat(r.failure()).isInstanceOf(failure);
        assertThat(committedOutputs(r.runId())).isZero();
        Path out = Path.of("target/pilot-it/" + specimen);
        assertThat(out.resolve("datasets")).doesNotExist();
        assertThat(out.resolve("pilot-failure.json")).exists();
    }

    @Test
    void INT_R10_unreadableInputFailsIntakeAndCommitsNoSnapshotOrOutputs() throws Exception {
        PilotRunService.PilotRunResult r = run("malformed-tcatbalf");
        assertFailedWithNothingCommitted(r, PilotFailure.UnreadableInput.class, "malformed-tcatbalf");
        assertThat(count("account_input", r.runId()) + count("category_balance_input", r.runId())).isZero();
    }

    @Test
    void INT_R10_missingInputDatasetFailsWithNoCommittedOutputs() throws Exception {
        PilotRunService.PilotRunResult r = run("missing-input");
        assertFailedWithNothingCommitted(r, PilotFailure.UnreadableInput.class, "missing-input");
        assertThat(count("account_input", r.runId())).isZero();
    }

    @Test
    void INT_R10_missingAccountRollsBackEarlierGroupOutputs() throws Exception {
        // Group 00000002001 computes a transaction before 00000002002's ACCTFILE read fails.
        PilotRunService.PilotRunResult r = run("missing-account");
        assertFailedWithNothingCommitted(r, PilotFailure.RequiredLookupMissing.class, "missing-account");
        assertThat(((PilotFailure.RequiredLookupMissing) r.failure()).dd()).isEqualTo("ACCTFILE");
    }

    @Test
    void INT_R10_missingXrefFailsWithNoCommittedOutputs() throws Exception {
        PilotRunService.PilotRunResult r = run("missing-xref");
        assertFailedWithNothingCommitted(r, PilotFailure.RequiredLookupMissing.class, "missing-xref");
        assertThat(((PilotFailure.RequiredLookupMissing) r.failure()).dd()).isEqualTo("XREFFILE");
    }

    @Test
    void INT_H08_missingDefaultRateFailsWithNoCommittedOutputs() throws Exception {
        PilotRunService.PilotRunResult r = run("missing-default-rate");
        assertFailedWithNothingCommitted(r, PilotFailure.RequiredLookupMissing.class, "missing-default-rate");
        assertThat(((PilotFailure.RequiredLookupMissing) r.failure()).dd()).isEqualTo("DISCGRP");
    }

    @Test
    void INT_R03_defaultFallbackUsesPaddedDefaultRow() throws Exception {
        PilotRunService.PilotRunResult r = run("default-fallback");
        assertThat(r.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForList("SELECT amount FROM interest_pilot.interest_tx WHERE run_id = ? ORDER BY emit_ordinal",
                BigDecimal.class, r.runId())).containsExactly(new BigDecimal("17.50"), new BigDecimal("37.00"));
        assertThat(Files.readAllLines(Path.of("target/pilot-it/default-fallback/sysout/INTCALC.STEP15.txt")))
                .contains("DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE");
    }

    @Test
    void m_INT_Q06_emptyCategoryBalancesCompleteWithNoTransactionsOrDeltas() throws Exception {
        PilotRunService.PilotRunResult r = run("empty-tcatbalf");
        assertThat(r.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(count("interest_tx", r.runId()) + count("account_delta", r.runId())).isZero();
        assertThat(Files.readAllLines(Path.of("target/pilot-it/empty-tcatbalf/datasets/"
                + FixtureDatasets.SYSTRAN_DSN + ".txt"))).isEmpty();
    }
}
