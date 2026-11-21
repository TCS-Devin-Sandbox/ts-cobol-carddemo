package com.carddemo.batch.statements;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.carddemo.batch.common.BatchArtifacts;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** CREASTMT: DELDEF01 (IDCAMS), STEP010 (SORT), STEP020 (REPRO), STEP030 (IEFBR14), STEP040 (CBSTM03A). */
@Configuration
class CreateStatementJob {
    static final String JOB = "CREASTMT";
    static final String TRXFL_SEQ = "AWS.M2.CARDDEMO.TRXFL.SEQ";
    static final String TRXFL_KSDS = "AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS";
    static final String STATEMENT_TEXT = "AWS.M2.CARDDEMO.STATEMNT.PS";
    static final String STATEMENT_HTML = "AWS.M2.CARDDEMO.STATEMNT.HTML";
    static final String RETURN_CODES = "returnCodes";

    interface StepBody {
        int run(Path out) throws Exception;
    }

    @Bean
    Job creastmt(JobRepository repository, PlatformTransactionManager transactions, JdbcTemplate jdbc) {
        var store = new StatementStore(jdbc);
        return new JobBuilder(JOB.toLowerCase(), repository)
                .start(step("DELDEF01", false, repository, transactions, out -> {
                    Files.deleteIfExists(dataset(out, TRXFL_SEQ));
                    Files.deleteIfExists(dataset(out, TRXFL_KSDS));
                    return 0;
                }))
                .next(step("STEP010", false, repository, transactions, out -> {
                    var sorted = TransactionSort.sortAndReformat(store.transactionRecords());
                    write(out, TRXFL_SEQ, sorted);
                    BatchArtifacts.writeSysout(out, JOB, "STEP010",
                            "ICE000I SORT: " + sorted.size() + " RECORDS OUT, LRECL " + TransactionSort.LRECL + "\n");
                    return 0;
                }))
                .next(step("STEP020", true, repository, transactions, out -> {
                    var records = read(out, TRXFL_SEQ);
                    for (int i = 1; i < records.size(); i++) {
                        if (key(records.get(i - 1)).compareTo(key(records.get(i))) >= 0) {
                            throw new IllegalStateException("REPRO into the TRXFL KSDS needs ascending unique keys");
                        }
                    }
                    write(out, TRXFL_KSDS, records);
                    BatchArtifacts.writeSysout(out, JOB, "STEP020", "REPRO INFILE(INFILE) OUTFILE(OUTFILE)\n"
                            + "IDC0005I NUMBER OF RECORDS PROCESSED WAS " + String.format("%09d", records.size()) + "\n"
                            + "IDC0001I FUNCTION COMPLETED, HIGHEST CONDITION CODE WAS 0\n"
                            + "IDC0002I IDCAMS PROCESSING COMPLETE. MAXIMUM CONDITION CODE WAS 0\n");
                    return 0;
                }))
                .next(step("STEP030", true, repository, transactions, out -> {
                    Files.deleteIfExists(dataset(out, STATEMENT_TEXT));
                    Files.deleteIfExists(dataset(out, STATEMENT_HTML));
                    return 0;
                }))
                .next(step("STEP040", true, repository, transactions, out -> {
                    var result = StatementProgram.run(read(out, TRXFL_KSDS), store);
                    write(out, STATEMENT_TEXT, result.text());
                    write(out, STATEMENT_HTML, result.html());
                    BatchArtifacts.writeSysout(out, JOB, "STEP040", result.sysout());
                    return result.returnCode();
                }))
                .build();
    }

    /** {@code conditional} steps carry COND=(0,NE): bypassed (no return code) once an earlier step ended non-zero. */
    @SuppressWarnings("unchecked")
    private static Step step(String name, boolean conditional, JobRepository repository,
            PlatformTransactionManager transactions, StepBody body) {
        return new StepBuilder(name, repository).tasklet((contribution, chunk) -> {
            var jobContext = chunk.getStepContext().getStepExecution().getJobExecution().getExecutionContext();
            var codes = new LinkedHashMap<String, Integer>(
                    (Map<String, Integer>) jobContext.get(RETURN_CODES, Map.class, Map.of()));
            if (!conditional || codes.values().stream().allMatch(rc -> rc == 0)) {
                Path out = Path.of(chunk.getStepContext().getStepExecution().getJobParameters().getString("out"));
                codes.put(name, body.run(out));
                jobContext.put(RETURN_CODES, codes);
            }
            return RepeatStatus.FINISHED;
        }, transactions).build();
    }

    private static String key(String record) {
        return StatementProgram.pad(record, 32);
    }

    static Path dataset(Path out, String name) {
        return out.resolve("datasets").resolve(name + ".txt");
    }

    private static List<String> read(Path out, String name) throws Exception {
        return Files.readAllLines(dataset(out, name), StandardCharsets.ISO_8859_1);
    }

    private static void write(Path out, String name, List<String> records) throws Exception {
        Files.createDirectories(out.resolve("datasets"));
        var content = new StringBuilder();
        for (String record : records) content.append(record.stripTrailing()).append('\n');
        Files.writeString(dataset(out, name), content, StandardCharsets.ISO_8859_1);
    }
}
