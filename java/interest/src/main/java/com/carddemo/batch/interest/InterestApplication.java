package com.carddemo.batch.interest;

import static com.carddemo.batch.common.VsamDataset.ACCOUNT;
import static com.carddemo.batch.common.VsamDataset.CARD_XREF;
import static com.carddemo.batch.common.VsamDataset.CATEGORY_BALANCE;
import static com.carddemo.batch.common.VsamDataset.DISCLOSURE;
import static com.carddemo.batch.common.VsamDataset.TRANSACTION;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.ChainRun;
import com.carddemo.batch.common.StepCheckpoint;
import com.carddemo.batch.common.VsamTransfer;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootApplication
public class InterestApplication {
    private static final String DSN_PREFIX = "AWS.M2.CARDDEMO.";
    private static final String INTCALC_PARM = "2022071800";

    @Bean
    Job interestJob(JobRepository repository, PlatformTransactionManager manager, InterestTasklet tasklet) {
        return new JobBuilder("INTCALC", repository)
                .start(new StepBuilder("STEP15", repository).tasklet(StepCheckpoint.once(tasklet), manager).build())
                .build();
    }

    public static void main(String[] args) throws Exception {
        var options = parseArguments(args);
        var input = Path.of(options.get("--input"));
        var out = Path.of(options.get("--out"));
        var clock = LocalDateTime.parse(options.get("--clock"), DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SS"));
        String url = System.getenv().getOrDefault("INTCALC_JDBC_URL", "jdbc:postgresql://localhost:5432/carddemo");
        String user = System.getenv().getOrDefault("PGUSER", "carddemo");
        String password = System.getenv().getOrDefault("PGPASSWORD", "carddemo");
        var parameters = ChainRun.parameters(input, out).addString("parmDate", INTCALC_PARM)
                .addLocalDateTime("clock", clock).toJobParameters();
        ChainRun.run(InterestApplication.class, "intcalc", options.get("--run-id"), url, user, password, parameters,
                connection -> {
                    var transfer = new VsamTransfer(connection);
                    for (var dataset : List.of(ACCOUNT, CARD_XREF, CATEGORY_BALANCE, DISCLOSURE)) {
                        transfer.load(dataset, input.resolve(DSN_PREFIX + dataset.inputName + ".PS.txt"));
                    }
                }, (dataSource, repository, execution) -> {
                    try (var connection = dataSource.getConnection()) {
                        var transfer = new VsamTransfer(connection);
                        transfer.export(ACCOUNT, out.resolve("datasets/" + DSN_PREFIX + "ACCTDATA.VSAM.KSDS.txt"));
                        transfer.export(TRANSACTION, out.resolve("datasets/" + DSN_PREFIX + "SYSTRAN.G0001V00.txt"));
                    }
                    BatchArtifacts.writeReturnCodes(out, Map.of("INTCALC",
                            Map.of("STEP05", 0, "STEP10", 0, "STEP15", 0, "STEP20", 0, "STEP25", 0, "STEP30", 0)));
                });
    }

    private static Map<String, String> parseArguments(String[] args) {
        var options = new HashMap<String, String>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i], args[i + 1]);
        }
        var required = Set.of("--chain", "--case", "--input", "--out", "--clock");
        if (args.length != options.size() * 2 || !options.keySet().containsAll(required)
                || options.keySet().stream().anyMatch(key -> !required.contains(key) && !key.equals("--run-id"))
                || !"interest".equals(options.get("--chain"))) {
            throw new IllegalArgumentException("Expected --chain interest --case NAME --input DIR --out DIR --clock TIMESTAMP [--run-id ID]");
        }
        return options;
    }
}
