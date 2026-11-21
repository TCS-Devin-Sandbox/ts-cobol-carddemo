package com.carddemo.batch.posting;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.VsamDataset;
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
        if (!option(options, "--chain").equals("posting")) {
            throw new IllegalArgumentException("The POSTTRAN candidate only supports the posting chain");
        }
        Path input = Path.of(option(options, "--input"));
        Path out = Path.of(option(options, "--out"));
        var at = LocalDateTime.parse(option(options, "--clock"), HARNESS_CLOCK).toInstant(UTC);

        String url = System.getenv().getOrDefault("POSTING_JDBC_URL", "jdbc:postgresql://localhost:5432/carddemo");
        String user = System.getenv().getOrDefault("PGUSER", "carddemo");
        String password = System.getenv().getOrDefault("PGPASSWORD", "carddemo");
        String schema = "posting_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        var dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setSchema(schema);

        admin.execute("CREATE SCHEMA " + schema);
        try (var context = new SpringApplicationBuilder(PostingCandidate.class)
                .web(WebApplicationType.NONE)
                .initializers(ctx -> ctx.getBeanFactory().registerSingleton("dataSource", dataSource))
                .run("--spring.batch.job.enabled=false", "--spring.batch.jdbc.initialize-schema=always",
                        "--spring.flyway.default-schema=" + schema, "--spring.main.banner-mode=off")) {
            try (var connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                var transfer = new VsamTransfer(connection);
                for (var dataset : new VsamDataset[]{VsamDataset.ACCOUNT, VsamDataset.CARD_XREF, VsamDataset.CATEGORY_BALANCE}) {
                    transfer.load(dataset, input.resolve(PREFIX + dataset.inputName + ".PS.txt"));
                }
                connection.commit();
            }
            var parameters = new JobParametersBuilder()
                    .addString("input", input.toString())
                    .addString("out", out.toString())
                    .addString("clock", at.toString())
                    .toJobParameters();
            var result = context.getBean(JobLauncher.class).run(context.getBean(Job.class), parameters);
            if (result.getStatus() != BatchStatus.COMPLETED) {
                throw new IllegalStateException("POSTTRAN failed: " + result.getExitStatus());
            }
            var step = result.getStepExecutions().iterator().next();
            try (var connection = dataSource.getConnection()) {
                var transfer = new VsamTransfer(connection);
                Path datasets = out.resolve("datasets");
                transfer.export(VsamDataset.ACCOUNT, datasets.resolve(PREFIX + "ACCTDATA.VSAM.KSDS.txt"));
                transfer.export(VsamDataset.CATEGORY_BALANCE, datasets.resolve(PREFIX + "TCATBALF.VSAM.KSDS.txt"));
                transfer.export(VsamDataset.TRANSACTION, datasets.resolve(PREFIX + "TRANSACT.VSAM.KSDS.txt"));
            }
            BatchArtifacts.writeReturnCodes(out, Map.of("POSTTRAN", Map.of(
                    "STEP15", step.getExecutionContext().getInt("returnCode"),
                    "CLCIFIL", 0, "OPCIFIL", 0, "STEP05", 0, "STEP10", 0, "STEP20", 0, "STEP25", 0, "STEP30", 0)));
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
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
