package com.carddemo.interest.pilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;

/** INT-01 acceptance on the real i01-baseline inputs; expectations come from the i01 goldens and the ticket. */
class PilotI01IT extends PilotPostgresTestSupport {

    static final Path OUT = Path.of("target/pilot-it/i01-baseline").toAbsolutePath();
    static final Path EVIDENCE = Path.of("target/pilot-evidence").toAbsolutePath();

    @Test
    void INT_R01_R05_R08_Q05_Q02_i01_stagesTwoTransactionsAndOneDelta() throws Exception {
        PilotRunService.PilotRunResult result = service.run(I01.resolve("input"), OUT);
        assertThat(result.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(result.exitCode()).isZero();

        List<Map<String, Object>> txs = jdbc.queryForList("""
                SELECT txn_id, txn_type, category_code, source, description, amount, merchant_id, card_number,
                       orig_ts, proc_ts FROM interest_pilot.interest_tx WHERE run_id = ? ORDER BY emit_ordinal""",
                result.runId());
        assertThat(txs).hasSize(2);
        assertThat(txs).extracting(r -> r.get("txn_id")).containsExactly("2022071800000001", "2022071800000002");
        assertThat(txs).extracting(r -> r.get("amount"))
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("37.00"));
        assertThat(txs).extracting(r -> r.get("card_number")).containsExactly("4000000000002001", "4000000000002002");
        assertThat(txs).allSatisfy(r -> {
            assertThat(r.get("txn_type")).isEqualTo("01");
            assertThat(r.get("category_code")).isEqualTo("0005");
            assertThat(r.get("source")).isEqualTo("System    ");
            assertThat(r.get("merchant_id")).isEqualTo("000000000");
            assertThat(r.get("orig_ts")).isEqualTo("2022-07-05-10.00.00.000000");
            assertThat(r.get("proc_ts")).isEqualTo("2022-07-05-10.00.00.000000");
        });
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM interest_pilot.interest_tx WHERE run_id = ?",
                BigDecimal.class, result.runId())).isEqualTo(new BigDecimal("47.00"));

        List<Map<String, Object>> deltas = jdbc.queryForList(
                "SELECT * FROM interest_pilot.account_delta WHERE run_id = ?", result.runId());
        assertThat(deltas).singleElement().satisfies(d -> {
            assertThat(d.get("acct_id")).isEqualTo("00000002001");
            assertThat(d.get("old_current_balance")).isEqualTo(new BigDecimal("1000.00"));
            assertThat(d.get("new_current_balance")).isEqualTo(new BigDecimal("1010.00"));
            assertThat(d.get("new_cycle_credit")).isEqualTo(new BigDecimal("0.00"));
            assertThat(d.get("new_cycle_debit")).isEqualTo(new BigDecimal("0.00"));
        });
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM interest_pilot.account_delta WHERE run_id = ? AND acct_id = '00000002002'""",
                Integer.class, result.runId())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT current_balance FROM interest_pilot.account_input WHERE run_id = ? AND acct_id = '00000002002'""",
                BigDecimal.class, result.runId())).isEqualTo(new BigDecimal("2400.00"));

        writeFieldSliceEvidence(result);
    }

    /** T01-T13 and A03/A09-A10 slices of the exported datasets against the decoded i01 golden records. */
    private void writeFieldSliceEvidence(PilotRunService.PilotRunResult result) throws IOException {
        List<String> report = new ArrayList<>();
        report.add("# INT-01 field slice: i01-baseline (pilot candidate vs golden)");
        report.add("");
        report.add("run_id: " + result.runId());
        report.add("");
        report.add("| Dataset | Record | Slice | Golden | Pilot | Match |");
        report.add("|---|---|---|---|---|---|");
        List<String> goldenTx = records(I01.resolve("expected/datasets/" + FixtureDatasets.SYSTRAN_DSN + ".txt"), 350);
        List<String> pilotTx = records(OUT.resolve("datasets/" + FixtureDatasets.SYSTRAN_DSN + ".txt"), 350);
        assertThat(pilotTx).hasSameSizeAs(goldenTx);
        String[] tNames = {"T01 TRAN-ID", "T02 TRAN-TYPE-CD", "T03 TRAN-CAT-CD", "T04 TRAN-SOURCE", "T05 TRAN-DESC",
                "T06 TRAN-AMT", "T07 TRAN-MERCHANT-ID", "T08 TRAN-MERCHANT-NAME", "T09 TRAN-MERCHANT-CITY",
                "T10 TRAN-MERCHANT-ZIP", "T11 TRAN-CARD-NUM", "T12 TRAN-ORIG-TS", "T13 TRAN-PROC-TS"};
        int[] tWidths = {16, 2, 4, 10, 100, 11, 9, 50, 50, 10, 16, 26, 26};
        int matched = 0;
        long cents = 0;
        for (int row = 0; row < goldenTx.size(); row++) {
            int at = 0;
            for (int f = 0; f < tNames.length; f++) {
                String g = goldenTx.get(row).substring(at, at + tWidths[f]);
                String p = pilotTx.get(row).substring(at, at + tWidths[f]);
                assertThat(p).as("SYSTRAN row %d %s", row + 1, tNames[f]).isEqualTo(g);
                report.add("| SYSTRAN | " + (row + 1) + " | " + tNames[f] + " | `" + g.stripTrailing() + "` | `"
                        + p.stripTrailing() + "` | yes |");
                at += tWidths[f];
            }
            matched++;
            cents += PilotFieldCodec.signedZoned(goldenTx.get(row).getBytes(StandardCharsets.ISO_8859_1), 132, 11, 2,
                    "golden", row + 1).movePointRight(2).longValueExact();
        }
        List<String> goldenAcct = records(I01.resolve("expected/datasets/" + FixtureDatasets.ACCOUNT_DSN + ".txt"), 300);
        List<String> pilotAcct = records(OUT.resolve("datasets/" + FixtureDatasets.ACCOUNT_DSN + ".txt"), 300);
        assertThat(pilotAcct).hasSameSizeAs(goldenAcct);
        String[] aNames = {"A03 ACCT-CURR-BAL", "A09 ACCT-CURR-CYC-CREDIT", "A10 ACCT-CURR-CYC-DEBIT"};
        int[] aOffsets = {12, 78, 90};
        for (int row = 0; row < goldenAcct.size(); row++) {
            for (int f = 0; f < aNames.length; f++) {
                String g = goldenAcct.get(row).substring(aOffsets[f], aOffsets[f] + 12);
                String p = pilotAcct.get(row).substring(aOffsets[f], aOffsets[f] + 12);
                assertThat(p).as("ACCTDATA row %d %s", row + 1, aNames[f]).isEqualTo(g);
                report.add("| ACCTDATA | " + (row + 1) + " | " + aNames[f] + " | `" + g + "` | `" + p + "` | yes |");
            }
            assertThat(pilotAcct.get(row)).as("ACCTDATA row %d whole record", row + 1).isEqualTo(goldenAcct.get(row));
            matched++;
        }
        report.add("");
        report.add("Matched rows: " + matched + " (SYSTRAN " + goldenTx.size() + ", ACCTDATA " + goldenAcct.size()
                + ", including untouched account 00000002002). Interest cents matched: " + cents + ".");
        report.add("This is a field slice (T01-T13, A03/A09-A10 plus whole ACCTDATA records), not full parity.");
        Files.createDirectories(EVIDENCE);
        Files.write(EVIDENCE.resolve("i01-field-slice.md"), report);
        assertThat(cents).isEqualTo(4700);
    }

    private static List<String> records(Path file, int lrecl) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
            out.add(line + " ".repeat(lrecl - line.length()));
        }
        return out;
    }
}
