package com.carddemo.batch.posting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.ChainRun;
import com.carddemo.batch.common.VsamDataset;
import com.carddemo.batch.common.VsamTransfer;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

import static com.carddemo.batch.posting.PostingTasklet.PREFIX;
import static java.time.ZoneOffset.UTC;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(PostingJob.class)
public class PostingCandidate {
    private static final DateTimeFormatter HARNESS_CLOCK = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss.SS", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    public static void main(String[] args) throws Exception {
        var options = options(args);
        String url = System.getenv().getOrDefault("POSTING_JDBC_URL", "jdbc:postgresql://localhost:5432/carddemo");
        String user = System.getenv().getOrDefault("PGUSER", "carddemo");
        String password = System.getenv().getOrDefault("PGPASSWORD", "carddemo");
        Path out = Path.of(option(options, "--out"));
        switch (option(options, "--chain")) {
            case "posting" -> {
                Path input = Path.of(option(options, "--input"));
                var at = LocalDateTime.parse(option(options, "--clock"), HARNESS_CLOCK).toInstant(UTC);
                run(input, out, at.toString(), options.get("--run-id"), url, user, password);
            }
            case "tranrept" -> runReport(out, LocalDate.parse(option(options, "--start-date")),
                    LocalDate.parse(option(options, "--end-date")), url, user, password);
            default -> throw new IllegalArgumentException("Expected posting or tranrept");
        }
    }

    static void runReport(Path out, LocalDate start, LocalDate end, String url, String user, String password)
            throws Exception {
        var parameters = new JobParametersBuilder().addString("out", out.toAbsolutePath().toString())
                .addString("startDate", start.toString()).addString("endDate", end.toString()).toJobParameters();
        ChainRun.run(TransactionReportJob.class, "tranrept", null, url, user, password, parameters,
                connection -> new VsamTransfer(connection).stageFromPublic(VsamDataset.TRANSACTION,
                        VsamDataset.CARD_XREF, VsamDataset.TYPE, VsamDataset.CATEGORY),
                (dataSource, repository, execution) -> {
                    var job = execution.getJobInstance();
                    int rc = returnCode(repository, job, "TRANREPT.STEP10R");
                    BatchArtifacts.writeReturnCodes(out, Map.of(
                            "TRANREPT", Map.of("PRC001", returnCode(repository, job, "TRANREPT.PRC001"),
                                    "STEP05R", returnCode(repository, job, "TRANREPT.STEP05R"), "STEP10R", rc)));
                    if (rc != 0) throw new IllegalStateException("TRANREPT.STEP10R returned " + rc);
                });
    }

    static void run(Path input, Path out, String clock, String runId, String url, String user, String password)
            throws Exception {
        var parameters = ChainRun.parameters(input, out).addString("clock", clock).toJobParameters();
        ChainRun.run(PostingCandidate.class, "posting", runId, url, user, password, parameters,
                connection -> {
                    var transfer = new VsamTransfer(connection);
                    for (var dataset : new VsamDataset[]{VsamDataset.ACCOUNT, VsamDataset.CARD_XREF,
                            VsamDataset.CATEGORY_BALANCE}) {
                        transfer.load(dataset, input.resolve(PREFIX + dataset.inputName + ".PS.txt"));
                    }
                    for (var dataset : new VsamDataset[]{VsamDataset.TYPE, VsamDataset.CATEGORY}) {
                        Path file = input.resolve(PREFIX + dataset.inputName + ".PS.txt");
                        if (!Files.notExists(file)) transfer.load(dataset, file);
                    }
                }, (dataSource, repository, execution) -> {
                    try (var connection = dataSource.getConnection()) {
                        var transfer = new VsamTransfer(connection);
                        Path datasets = out.resolve("datasets");
                        transfer.export(VsamDataset.ACCOUNT, datasets.resolve(PREFIX + "ACCTDATA.VSAM.KSDS.txt"));
                        transfer.export(VsamDataset.CATEGORY_BALANCE, datasets.resolve(PREFIX + "TCATBALF.VSAM.KSDS.txt"));
                        transfer.export(VsamDataset.TRANSACTION, datasets.resolve(PREFIX + "TRANSACT.VSAM.KSDS.txt"));
                    }
                    var job = execution.getJobInstance();
                    BatchArtifacts.writeReturnCodes(out, Map.of(
                            "POSTTRAN", Map.of("STEP15", returnCode(repository, job, "POSTTRAN.STEP15"),
                                    "CLCIFIL", 0, "OPCIFIL", 0, "STEP05", 0, "STEP10", 0, "STEP20", 0, "STEP25", 0, "STEP30", 0),
                            "TRANREPT", Map.of("PRC001", returnCode(repository, job, "TRANREPT.PRC001"),
                                    "STEP05R", returnCode(repository, job, "TRANREPT.STEP05R"),
                                    "STEP10R", returnCode(repository, job, "TRANREPT.STEP10R"))));
                });
    }

    private static int returnCode(JobRepository repository, JobInstance job, String name) {
        return repository.getLastStepExecution(job, name).getExecutionContext().getInt("returnCode", 0);
    }

    private static Map<String, String> options(String[] args) {
        var parsed = new HashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length || !args[i].startsWith("--") || parsed.putIfAbsent(args[i], args[i + 1]) != null) {
                throw new IllegalArgumentException("Expected one value per option");
            }
        }
        return parsed;
    }

    private static String option(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }
}
