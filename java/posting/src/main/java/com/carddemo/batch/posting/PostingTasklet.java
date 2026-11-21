package com.carddemo.batch.posting;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.codec.CardDemoLayout;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.time.ZoneOffset.UTC;

final class PostingTasklet implements Tasklet {
    static final String PREFIX = "AWS.M2.CARDDEMO.";
    private static final DateTimeFormatter PROCESSING_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd-HH.mm.ss.SS'0000'", Locale.ROOT).withZone(UTC);
    private static final BigDecimal CYCLE_TEST_MODULUS = BigDecimal.TEN.pow(9);

    private final PostingStore store;
    private final Clock clock;

    PostingTasklet(PostingStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext context) throws Exception {
        var parameters = contribution.getStepExecution().getJobParameters();
        var input = Path.of(parameters.getString("input")).resolve(PREFIX + "DALYTRAN.PS.txt");
        var out = Path.of(parameters.getString("out"));
        var rejects = out.resolve("datasets").resolve(PREFIX + "DALYREJS.G0001V00.txt");
        Files.createDirectories(rejects.getParent());
        var layout = CardDemoLayout.CVTRA06Y.load();
        var codec = new FixedRecordCodec(layout);
        String at = parameters.getString("clock");
        String processedTimestamp = PROCESSING_TIME.format(at == null ? clock.instant() : Instant.parse(at));
        var sysout = new StringBuilder("START OF EXECUTION OF PROGRAM CBTRN02C\n");
        int rejected = 0;
        store.openTransactionOutput();
        try (var dailyInput = Files.newBufferedReader(input, ISO_8859_1);
                var rejectOutput = Files.newBufferedWriter(rejects, ISO_8859_1)) {
            for (String line; (line = dailyInput.readLine()) != null; ) {
                contribution.incrementReadCount();
                if (line.length() > layout.length()) {
                    throw new IllegalArgumentException("DALYTRAN record too long at line " + contribution.getReadCount());
                }
                String record = line + " ".repeat(layout.length() - line.length());
                var daily = codec.decode(record.getBytes(ISO_8859_1));
                var rejection = post(daily, processedTimestamp, sysout);
                if (rejection == null) {
                    contribution.incrementWriteCount(1);
                } else {
                    rejected++;
                    // The original record, including its filler and zoned signs, is copied verbatim.
                    rejectOutput.write(record);
                    rejectOutput.write(String.format(Locale.ROOT, "%04d%s\n", rejection.code, rejection.description));
                }
            }
        }
        sysout.append(String.format(Locale.ROOT, "TRANSACTIONS PROCESSED :%09d\nTRANSACTIONS REJECTED  :%09d\n",
                contribution.getReadCount(), rejected));
        sysout.append("END OF EXECUTION OF PROGRAM CBTRN02C\n");
        BatchArtifacts.writeSysout(out, "POSTTRAN", "STEP15", sysout.toString());
        contribution.getStepExecution().getExecutionContext().putInt("returnCode", rejected == 0 ? 0 : 4);
        return RepeatStatus.FINISHED;
    }

    private Rejection post(Map<String, Object> daily, String processedTimestamp, StringBuilder sysout) {
        Long accountId = store.findAccountId((String) daily.get("DALYTRAN-CARD-NUM"));
        if (accountId == null) return Rejection.INVALID_CARD;
        var account = store.findAccount(accountId);
        if (account == null) return Rejection.MISSING_ACCOUNT;

        var amount = (BigDecimal) daily.get("DALYTRAN-AMT");
        // Expiration is checked last in COBOL and supersedes an overlimit rejection.
        var originDate = ((String) daily.get("DALYTRAN-ORIG-TS")).substring(0, 10);
        if (account.expirationDate().compareTo(originDate) < 0) return Rejection.EXPIRED;
        var cycleBalance = account.cycleCredit().subtract(account.cycleDebit()).add(amount)
                .remainder(CYCLE_TEST_MODULUS);
        if (account.creditLimit().compareTo(cycleBalance) < 0) return Rejection.OVERLIMIT;

        String type = (String) daily.get("DALYTRAN-TYPE-CD");
        int category = ((BigDecimal) daily.get("DALYTRAN-CAT-CD")).intValueExact();
        if (store.addCategoryAmount(accountId, type, category, amount)) {
            sysout.append(String.format(Locale.ROOT,
                    "TCATBAL record not found for key : %011d%s%04d.. Creating.\n", accountId, type, category));
        }
        store.addAccountAmount(accountId, amount);
        store.writeTransaction(daily, processedTimestamp);
        return null;
    }

    private enum Rejection {
        INVALID_CARD(100, "INVALID CARD NUMBER FOUND"),
        MISSING_ACCOUNT(101, "ACCOUNT RECORD NOT FOUND"),
        OVERLIMIT(102, "OVERLIMIT TRANSACTION"),
        EXPIRED(103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION");

        private final int code;
        private final String description;

        Rejection(int code, String description) {
            this.code = code;
            this.description = description;
        }
    }
}
