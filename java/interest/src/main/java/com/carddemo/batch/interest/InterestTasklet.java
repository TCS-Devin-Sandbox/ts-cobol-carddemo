package com.carddemo.batch.interest;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.codec.CardDemoLayout;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class InterestTasklet implements Tasklet {
    private static final BigDecimal MONTHLY_DIVISOR = BigDecimal.valueOf(1200);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd-HH.mm.ss.SS", Locale.ROOT);
    private final JdbcTemplate jdbc;
    private final JdbcClient records;
    private final Clock clock;

    InterestTasklet(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.records = JdbcClient.create(jdbc);
        this.clock = clock;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext context) throws Exception {
        var parameters = contribution.getStepExecution().getJobParameters();
        String parmDate = parameters.getString("parmDate");
        var processingTime = parameters.getLocalDateTime("clock");
        String timestamp = (processingTime == null ? LocalDateTime.now(clock) : processingTime)
                .format(TIMESTAMP) + "0000";
        var layout = CardDemoLayout.CVTRA01Y.load();
        var codec = new FixedRecordCodec(layout);
        String filler = layout.fields().getLast().name();
        var sysout = new StringBuilder("START OF EXECUTION OF PROGRAM CBACT04C\n");
        jdbc.query(connection -> {
            var statement = connection.prepareStatement("""
                    SELECT account_id, transaction_type_code, transaction_category_code, balance, filler
                      FROM transaction_category_balance
                     ORDER BY account_id, transaction_type_code COLLATE "C", transaction_category_code
                    """);
            statement.setFetchSize(500);
            return statement;
        }, rows -> {
            Long lastAccount = null;
            String group = null;
            String card = null;
            BigDecimal total = BigDecimal.ZERO;
            int sequence = 0;
            while (rows.next()) {
                long account = rows.getLong("account_id");
                String type = rows.getString("transaction_type_code");
                int category = rows.getInt("transaction_category_code");
                BigDecimal balance = rows.getBigDecimal("balance");
                sysout.append(new String(codec.encode(Map.of(
                        "TRANCAT-ACCT-ID", BigDecimal.valueOf(account),
                        "TRANCAT-TYPE-CD", type,
                        "TRANCAT-CD", BigDecimal.valueOf(category),
                        "TRAN-CAT-BAL", balance,
                        filler, rows.getString("filler"))), ISO_8859_1)).append('\n');

                if (lastAccount == null || account != lastAccount) {
                    if (lastAccount != null) rewriteAccount(lastAccount, total);
                    total = BigDecimal.ZERO;
                    lastAccount = account;
                    group = records.sql("SELECT account_group_id FROM account WHERE account_id = ?")
                            .param(account).query(String.class).single();
                    card = records.sql("SELECT card_number FROM card_xref WHERE account_id = ?")
                            .param(account).query(String.class).single();
                }

                var rate = findRate(group, type, category);
                if (rate.isEmpty()) {
                    sysout.append("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n");
                    rate = findRate("DEFAULT", type, category);
                }
                BigDecimal annualRate = rate.orElseThrow(() ->
                        new IllegalStateException("Missing default interest rate for " + type + "/" + category));
                if (annualRate.signum() != 0) {
                    BigDecimal interest = balance.multiply(annualRate).divide(MONTHLY_DIVISOR, 2, RoundingMode.DOWN);
                    total = total.add(interest);
                    writeTransaction(parmDate, ++sequence, account, card, interest, timestamp);
                }
            }
            // CBACT04C rewrites only on an account break, not at EOF.
            return null;
        });
        sysout.append("END OF EXECUTION OF PROGRAM CBACT04C\n");
        BatchArtifacts.writeSysout(Path.of(parameters.getString("out")), "INTCALC", "STEP15", sysout.toString());
        return RepeatStatus.FINISHED;
    }

    private void rewriteAccount(long account, BigDecimal total) {
        records.sql("""
                UPDATE account
                   SET current_balance = current_balance + ?, current_cycle_credit = 0, current_cycle_debit = 0
                 WHERE account_id = ?
                """).params(total, account).update();
    }

    private Optional<BigDecimal> findRate(String group, String type, int category) {
        return records.sql("""
                SELECT interest_rate FROM disclosure_group
                 WHERE account_group_id = ? AND transaction_type_code = ? AND transaction_category_code = ?
                """).params(group, type, category).query(BigDecimal.class).optional();
    }

    private void writeTransaction(String parmDate, int sequence, long account, String card,
                                  BigDecimal interest, String timestamp) {
        String transactionId = parmDate + String.format(Locale.ROOT, "%06d", sequence);
        String description = String.format(Locale.ROOT, "Int. for a/c %011d", account);
        records.sql("""
                INSERT INTO "transaction" (transaction_id, transaction_type_code, transaction_category_code,
                    source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip_code,
                    card_number, origination_timestamp, processed_timestamp, filler)
                VALUES (?, '01', 5, 'System', ?, ?, 0, '', '', '', ?, ?, ?, '')
                """).params(transactionId, description, interest, card, timestamp, timestamp).update();
    }
}
