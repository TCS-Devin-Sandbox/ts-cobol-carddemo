package com.carddemo.batch.interest;

import static com.carddemo.batch.common.VsamDataset.ACCOUNT;
import static com.carddemo.batch.common.VsamDataset.CARD_XREF;
import static com.carddemo.batch.common.VsamDataset.CATEGORY_BALANCE;
import static com.carddemo.batch.common.VsamDataset.DISCLOSURE;
import static com.carddemo.batch.common.VsamDataset.TRANSACTION;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.VsamTransfer;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootApplication
public class InterestApplication {
    private static final String DSN_PREFIX = "AWS.M2.CARDDEMO.";
    private static final String INTCALC_PARM = "2022071800";

    @Bean
    Job interestJob(JobRepository repository, PlatformTransactionManager manager, InterestTasklet tasklet) {
        return new JobBuilder("INTCALC", repository)
                .start(new StepBuilder("STEP15", repository).tasklet(tasklet, manager).build())
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
        String schema = "intcalc_" + UUID.randomUUID().toString().replace("-", "");

        var app = new SpringApplication(InterestApplication.class);
        app.addInitializers(context -> context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("intcalcCandidate", Map.of(
                        "spring.datasource.url", url,
                        "spring.datasource.username", user,
                        "spring.datasource.password", password,
                        "spring.datasource.hikari.schema", schema,
                        "spring.flyway.default-schema", schema,
                        "spring.batch.job.enabled", false,
                        "spring.batch.jdbc.initialize-schema", "always"))));

        try (var admin = DriverManager.getConnection(url, user, password); var sql = admin.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try (var context = app.run()) {
                var dataSource = context.getBean(DataSource.class);
                try (var connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    var transfer = new VsamTransfer(connection);
                    for (var dataset : List.of(ACCOUNT, CARD_XREF, CATEGORY_BALANCE, DISCLOSURE)) {
                        transfer.load(dataset, input.resolve(DSN_PREFIX + dataset.inputName + ".PS.txt"));
                    }
                    connection.commit();
                }
                var execution = context.getBean(JobLauncher.class).run(context.getBean(Job.class),
                        new JobParametersBuilder().addString("parmDate", INTCALC_PARM)
                                .addString("out", out.toString()).addLocalDateTime("clock", clock).toJobParameters());
                if (execution.getStatus() != BatchStatus.COMPLETED) {
                    throw new IllegalStateException("INTCALC failed: " + execution.getExitStatus());
                }
                try (var connection = dataSource.getConnection()) {
                    var transfer = new VsamTransfer(connection);
                    transfer.export(ACCOUNT, out.resolve("datasets/" + DSN_PREFIX + "ACCTDATA.VSAM.KSDS.txt"));
                    transfer.export(TRANSACTION, out.resolve("datasets/" + DSN_PREFIX + "SYSTRAN.G0001V00.txt"));
                }
                BatchArtifacts.writeReturnCodes(out, Map.of("INTCALC",
                        Map.of("STEP05", 0, "STEP10", 0, "STEP15", 0, "STEP20", 0, "STEP25", 0, "STEP30", 0)));
            } finally {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private static Map<String, String> parseArguments(String[] args) {
        var options = new HashMap<String, String>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i], args[i + 1]);
        }
        if (args.length != options.size() * 2
                || !options.keySet().equals(Set.of("--chain", "--case", "--input", "--out", "--clock"))
                || !"interest".equals(options.get("--chain"))) {
            throw new IllegalArgumentException("Expected --chain interest --case NAME --input DIR --out DIR --clock TIMESTAMP");
        }
        return options;
    }
}
