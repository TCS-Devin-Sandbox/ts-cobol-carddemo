package com.carddemo.interest.pilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.batch.core.BatchStatus;

/** Failure paths: the run fails and no pilot output for that run is committed (INT-R10). */
@ExtendWith(OutputCaptureExtension.class)
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
    void INT_R03_defaultFallbackUsesPaddedDefaultRow(CapturedOutput logs) throws Exception {
        PilotRunService.PilotRunResult r = run("default-fallback");
        assertThat(r.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForList("SELECT amount FROM interest_pilot.interest_tx WHERE run_id = ? ORDER BY emit_ordinal",
                BigDecimal.class, r.runId())).containsExactly(new BigDecimal("17.50"), new BigDecimal("37.00"));
        assertThat(Files.readAllLines(Path.of("target/pilot-it/default-fallback/sysout/INTCALC.STEP15.txt")))
                .contains("DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE");
        // S12/S14: ordinary fallback event with dd/op/status and no account or card identifiers.
        assertThat(logs.getOut()).contains("event=rate-fallback run=" + r.runId() + " dd=DISCGRP op=READ status=23")
                .doesNotContain("00000002001");
    }

    @Test
    void m_INT_Q06_emptyCategoryBalancesCompleteWithNoTransactionsOrDeltas() throws Exception {
        PilotRunService.PilotRunResult r = run("empty-tcatbalf");
        assertThat(r.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(count("interest_tx", r.runId()) + count("account_delta", r.runId())).isZero();
        assertThat(Files.readAllLines(Path.of("target/pilot-it/empty-tcatbalf/datasets/"
                + FixtureDatasets.SYSTRAN_DSN + ".txt"))).isEmpty();
    }

    @Test
    void INT_R10_failedRunIntoReusedOutputDirLeavesNoStaleSuccessArtifacts() throws Exception {
        Path out = Path.of("target/pilot-it/reused").toAbsolutePath();
        assertThat(service.run(I01.resolve("input"), out).exitCode()).isZero();
        assertThat(out.resolve("datasets")).exists();
        Files.writeString(out.resolve("datasets/operator-note.txt"), "kept");
        PilotRunService.PilotRunResult r = service.run(SPECIMENS.resolve("missing-account"), out);
        assertThat(r.exitCode()).isEqualTo(PilotRunService.FAILED_RC);
        assertThat(out.resolve("datasets/" + FixtureDatasets.SYSTRAN_DSN + ".txt")).doesNotExist();
        assertThat(out.resolve("datasets/" + FixtureDatasets.ACCOUNT_DSN + ".txt")).doesNotExist();
        assertThat(out.resolve(PilotExporter.SYSOUT_FILE)).doesNotExist();
        assertThat(out.resolve("datasets/operator-note.txt")).hasContent("kept");
        assertThat(Files.readString(out.resolve("rc.json"))).contains("\"maxrc\": 12");
        assertThat(out.resolve("pilot-failure.json")).exists();
        try (var entries = Files.list(out)) {
            assertThat(entries.map(p -> p.getFileName().toString()))
                    .containsExactlyInAnyOrder("rc.json", "pilot-failure.json", "datasets", "sysout");
        }
    }

    @Test
    void INT_R10_failureRcIsPublishedEvenWhenOldArtifactsCannotBeRemoved() throws Exception {
        Path out = Path.of("target/pilot-it/read-only").toAbsolutePath();
        assertThat(service.run(I01.resolve("input"), out).exitCode()).isZero();
        Path datasets = out.resolve("datasets");
        assertThat(datasets.toFile().setWritable(false)).isTrue();
        try {
            PilotRunService.PilotRunResult r = service.run(SPECIMENS.resolve("missing-account"), out);
            assertThat(r.exitCode()).isEqualTo(PilotRunService.FAILED_RC);
            assertThat(Files.readString(out.resolve("rc.json"))).contains("\"maxrc\": 12");
        } finally {
            datasets.toFile().setWritable(true);
        }
    }
}
