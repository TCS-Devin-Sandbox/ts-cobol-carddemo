package com.carddemo.batch.posting;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

class TransactionReportTest {
    @Test
    void sortUsesInclusiveProcessingDatesAndPreservesKeyOrderForTies(@TempDir Path out) throws Exception {
        var backup = TransactionReportJob.dataset(out, "TRANSACT.BKUP");
        Files.createDirectories(backup.getParent());
        String lastDate = record("1", "4000000000001002", "2022-07-06");
        String firstDate = record("2", "4000000000001001", "2022-01-01");
        String before = record("3", "4000000000001000", "2021-12-31");
        String after = record("4", "4000000000001000", "2022-07-07");
        String sameCard = record("5", "4000000000001001", "2022-02-01");
        String blankTimestamp = record("6", "4000000000001000", "").stripTrailing();
        List<String> input = List.of(lastDate, firstDate, before, after, sameCard, blankTimestamp);
        Files.write(backup, input, ISO_8859_1);

        TransactionReportJob.sort(out, new TransactionReportJob.DateRange("2022-01-01", "2022-07-06"));

        assertThat(Files.readAllLines(backup, ISO_8859_1)).containsExactlyElementsOf(input);
        assertThat(Files.readAllLines(TransactionReportJob.dataset(out, "TRANSACT.DALY"), ISO_8859_1))
                .containsExactly(firstDate, sameCard, lastDate);
    }

    @Test
    void reportRetainsFixedColumnsPaginationAndEofTotals() throws Exception {
        var output = new StringWriter();
        var report = new TransactionReport(output, "2022-01-01", "2022-07-06");
        for (int i = 0; i < 18; i++) {
            report.detail("ID", 1001, "01", "Example type description", 1, "Example category", "POS TERM",
                    new BigDecimal(i == 0 ? "-0.01" : "0.10"));
        }
        var sysout = new StringBuilder();
        report.endReport(sysout);

        var lines = output.toString().lines().toList();
        assertThat(lines.get(1)).isEmpty();
        assertThat(lines.get(3)).isEqualTo("-".repeat(133));
        assertThat(lines.get(4)).isEqualTo(
                "ID               00000001001 01-Example type de 0001-Example category              POS TERM      -           .01");
        assertThat(lines.get(20)).startsWith("Page Total ").endsWith("+          1.49");
        assertThat(lines.get(22)).isEqualTo(lines.getFirst());
        assertThat(lines.get(28)).startsWith("Page Total ").endsWith("+           .30");
        assertThat(lines.getLast()).startsWith("Grand Total").endsWith("+          1.79");
        assertThat(lines).noneMatch(line -> line.startsWith("Account Total"));
        assertThat(sysout.toString()).isEqualTo("TRAN-AMT 00000000010+\nWS-PAGE-TOTAL00000000020+\n");
    }

    @Test
    void reportDoesNotWriteHeadersOrTotalsWithoutDetails() throws Exception {
        var output = new StringWriter();
        var report = new TransactionReport(output, "2022-01-01", "2022-07-06");
        var sysout = new StringBuilder();
        report.endReport(sysout);
        assertThat(output.toString()).isEmpty();
        assertThat(sysout.toString()).isEmpty();
    }

    private static String record(String id, String card, String date) {
        return TransactionReport.field(id, 262) + card + " ".repeat(26) + date;
    }
}
