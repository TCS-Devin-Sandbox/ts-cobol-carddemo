package com.carddemo.batch.posting;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

import javax.sql.DataSource;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.VsamDataset;
import com.carddemo.batch.common.VsamTransfer;
import com.carddemo.batch.common.codec.CardDemoLayout;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;

import static com.carddemo.batch.posting.PostingTasklet.PREFIX;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

@Configuration(proxyBeanMethods = false)
class TransactionReportJob {
    private static final String BACKUP = "TRANSACT.BKUP";
    private static final String DAILY = "TRANSACT.DALY";
    private static final DateRange SORT_RANGE = new DateRange("2022-01-01", "2022-07-06");

    @Bean
    Job tranrept(JobRepository repository, PlatformTransactionManager transactions, DataSource dataSource,
            JdbcTemplate jdbc) {
        var repro = new StepBuilder("PRC001", repository).tasklet((contribution, context) -> {
            var out = Path.of(contribution.getStepExecution().getJobParameters().getString("out"));
            var connection = DataSourceUtils.getConnection(dataSource);
            try {
                new VsamTransfer(connection).export(VsamDataset.TRANSACTION, dataset(out, BACKUP));
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
            return RepeatStatus.FINISHED;
        }, transactions).build();
        var sort = new StepBuilder("STEP05R", repository).tasklet((contribution, context) -> {
            sort(Path.of(contribution.getStepExecution().getJobParameters().getString("out")));
            return RepeatStatus.FINISHED;
        }, transactions).build();
        var report = new StepBuilder("STEP10R", repository).tasklet((contribution, context) -> {
            var step = contribution.getStepExecution();
            var input = Path.of(step.getJobParameters().getString("input"));
            var out = Path.of(step.getJobParameters().getString("out"));
            step.getExecutionContext().putInt("returnCode", writeReport(input, out, jdbc));
            return RepeatStatus.FINISHED;
        }, transactions).build();
        return new JobBuilder("TRANREPT", repository).preventRestart().start(repro).next(sort).next(report).build();
    }

    static void sort(Path out) throws IOException {
        // JCL TRAN-CARD-NUM (263,16) and TRAN-PROC-DT (305,10); ties retain KSDS key order.
        var sorted = Files.readAllLines(dataset(out, BACKUP), ISO_8859_1).stream()
                .filter(row -> SORT_RANGE.includes(row.substring(304, 314)))
                .sorted(Comparator.comparing(row -> row.substring(262, 278)))
                .toList();
        Files.write(dataset(out, DAILY), sorted, ISO_8859_1);
    }

    static int writeReport(Path input, Path out, JdbcTemplate jdbc) throws Exception {
        var sysout = new StringBuilder("START OF EXECUTION OF PROGRAM CBTRN03C\n");
        var layout = CardDemoLayout.CVTRA05Y.load();
        var codec = new FixedRecordCodec(layout);
        int returnCode = 0;
        try (var dailyInput = Files.newBufferedReader(dataset(out, DAILY), ISO_8859_1);
                var output = Files.newBufferedWriter(dataset(out, "TRANREPT"), ISO_8859_1)) {
            String dates = readDateParameter(input.resolve(PREFIX + "DATEPARM.txt"));
            if (dates != null) {
                var range = DateRange.fromParameter(dates);
                sysout.append("Reporting from ").append(range.start()).append(" to ").append(range.end()).append('\n');
                var report = new TransactionReport(output, range.start(), range.end());
                String card = null;
                long account = 0;
                boolean reachedEof = true;
                for (String line; (line = dailyInput.readLine()) != null; ) {
                    var record = TransactionReport.field(line, layout.length());
                    var fields = codec.decode(record.getBytes(ISO_8859_1));
                    if (!range.includes(text(fields, "TRAN-PROC-TS").substring(0, 10))) {
                        // COBOL NEXT SENTENCE leaves the loop without writing the final totals.
                        reachedEof = false;
                        break;
                    }
                    sysout.append(record).append('\n');
                    String nextCard = text(fields, "TRAN-CARD-NUM");
                    if (!nextCard.equals(card)) {
                        report.endAccount();
                        card = nextCard;
                        account = lookup(jdbc, BigDecimal.class, "INVALID CARD NUMBER : " + card,
                                "SELECT account_id FROM card_xref WHERE card_number = ?", card).longValueExact();
                    }
                    String type = text(fields, "TRAN-TYPE-CD");
                    int category = ((BigDecimal) fields.get("TRAN-CAT-CD")).intValueExact();
                    String typeLabel = lookup(jdbc, String.class, "INVALID TRANSACTION TYPE : " + type,
                            "SELECT description FROM tran_type WHERE transaction_type_code = ?", type);
                    String categoryLabel = lookup(jdbc, String.class,
                            "INVALID TRAN CATG KEY : " + type + TransactionReport.categoryCode(category),
                            "SELECT description FROM tran_category WHERE transaction_type_code = ? AND transaction_category_code = ?",
                            type, category);
                    report.detail(text(fields, "TRAN-ID"), account, type, typeLabel, category,
                            categoryLabel, text(fields, "TRAN-SOURCE"), (BigDecimal) fields.get("TRAN-AMT"));
                }
                if (reachedEof) report.endReport(sysout);
            }
            sysout.append("END OF EXECUTION OF PROGRAM CBTRN03C\n");
        } catch (MissingLookup e) {
            returnCode = 12;
            sysout.append(e.getMessage()).append('\n').append("""
                    FILE STATUS IS: NNNN0023
                    ABENDING PROGRAM
                    CEE3ABD: USER ABEND U+0000000999

                    libcob: warning: implicit CLOSE of DATE-PARMS-FILE ('DATEPARM')
                    libcob: warning: implicit CLOSE of TRANCATG-FILE ('TRANCATG')
                    libcob: warning: implicit CLOSE of TRANTYPE-FILE ('TRANTYPE')
                    libcob: warning: implicit CLOSE of XREF-FILE ('CARDXREF')
                    libcob: warning: implicit CLOSE of REPORT-FILE ('TRANREPT')
                    libcob: warning: implicit CLOSE of TRANSACT-FILE ('TRANFILE')
                    """);
        }
        BatchArtifacts.writeSysout(out, "TRANREPT", "STEP10R", sysout.toString());
        return returnCode;
    }

    private static String readDateParameter(Path file) throws IOException {
        if (Files.notExists(file)) return null;
        try (var reader = Files.newBufferedReader(file, ISO_8859_1)) {
            return reader.readLine();
        }
    }

    private static <T> T lookup(JdbcTemplate jdbc, Class<T> type, String message, String sql, Object... keys) {
        return jdbc.query(sql, rows -> {
            if (!rows.next()) throw new MissingLookup(message);
            return rows.getObject(1, type);
        }, keys);
    }

    private static String text(Map<String, Object> fields, String name) {
        return (String) fields.get(name);
    }

    static Path dataset(Path out, String name) {
        return out.resolve("datasets").resolve(PREFIX + name + ".G0001V00.txt");
    }

    private record DateRange(String start, String end) {
        static DateRange fromParameter(String line) {
            String fields = TransactionReport.field(line, 21);
            return new DateRange(fields.substring(0, 10), fields.substring(11));
        }

        boolean includes(String date) {
            return date.compareTo(start) >= 0 && date.compareTo(end) <= 0;
        }
    }

    private static final class MissingLookup extends RuntimeException {
        MissingLookup(String message) {
            super(message);
        }
    }
}
