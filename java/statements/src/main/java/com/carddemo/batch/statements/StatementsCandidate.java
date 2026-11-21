package com.carddemo.batch.statements;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.VsamTransfer;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Parity candidate for the statements chain: masters load into a private PostgreSQL schema, then CREASTMT runs. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(CreateStatementJob.class)
public class StatementsCandidate {
    private static final Map<StatementStore.Master, String> INPUTS = Map.of(
            StatementStore.Master.ACCOUNT, "AWS.M2.CARDDEMO.ACCTDATA.PS.txt",
            StatementStore.Master.CARD_XREF, "AWS.M2.CARDDEMO.CARDXREF.PS.txt",
            StatementStore.Master.CUSTOMER, "AWS.M2.CARDDEMO.CUSTDATA.PS.txt",
            StatementStore.Master.TRANSACTION, "AWS.M2.CARDDEMO.DALYTRAN.PS.INIT.txt");

    public static void main(String[] args) throws Exception {
        var options = new HashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length || !args[i].startsWith("--")
                    || options.putIfAbsent(args[i], args[i + 1]) != null) {
                throw new IllegalArgumentException("Expected one value per option");
            }
        }
        if (!"statements".equals(options.get("--chain"))) {
            throw new IllegalArgumentException("The candidate only supports statements");
        }
        String url = System.getenv().getOrDefault("STATEMENTS_JDBC_URL", "jdbc:postgresql://localhost:5432/carddemo");
        run(Path.of(required(options, "--input")), Path.of(required(options, "--out")), url,
                System.getenv().getOrDefault("PGUSER", "carddemo"),
                System.getenv().getOrDefault("PGPASSWORD", "carddemo"));
    }

    static void run(Path input, Path out, String url, String user, String password) throws Exception {
        String schema = "statements_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        var dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setSchema(schema);
        admin.execute("CREATE SCHEMA " + schema);
        try (var context = new SpringApplicationBuilder(StatementsCandidate.class)
                .web(WebApplicationType.NONE)
                .initializers(ctx -> ctx.getBeanFactory().registerSingleton("dataSource", dataSource))
                .run("--spring.batch.job.enabled=false", "--spring.batch.jdbc.initialize-schema=always",
                        "--spring.flyway.default-schema=" + schema, "--spring.main.banner-mode=off")) {
            try (var connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                var transfer = new VsamTransfer(connection);
                for (var entry : INPUTS.entrySet()) {
                    Path file = input.resolve(entry.getValue());
                    if (Files.exists(file)) transfer.load(entry.getKey().dataset, file);
                }
                connection.commit();
            }
            var parameters = new JobParametersBuilder().addString("out", out.toString()).toJobParameters();
            var execution = context.getBean(JobLauncher.class).run(context.getBean("creastmt", Job.class), parameters);
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                throw new IllegalStateException("CREASTMT failed: " + execution.getAllFailureExceptions());
            }
            @SuppressWarnings("unchecked")
            var codes = (Map<String, Integer>) execution.getExecutionContext().get(CreateStatementJob.RETURN_CODES);
            BatchArtifacts.writeReturnCodes(out, Map.of(CreateStatementJob.JOB, codes));
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
}
