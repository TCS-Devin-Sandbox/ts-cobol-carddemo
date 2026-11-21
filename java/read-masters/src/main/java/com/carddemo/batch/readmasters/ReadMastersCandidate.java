package com.carddemo.batch.readmasters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
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

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(ReadMastersJobs.class)
public class ReadMastersCandidate {
    public static void main(String[] args) throws Exception {
        var options = new HashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length || !args[i].startsWith("--")
                    || options.putIfAbsent(args[i], args[i + 1]) != null) {
                throw new IllegalArgumentException("Expected one value per option");
            }
        }
        if (!"read-masters".equals(options.get("--chain"))) {
            throw new IllegalArgumentException("The candidate only supports read-masters");
        }
        String url = System.getenv().getOrDefault("READ_MASTERS_JDBC_URL", "jdbc:postgresql://localhost:5432/carddemo");
        run(Path.of(required(options, "--input")), Path.of(required(options, "--out")), url,
                System.getenv().getOrDefault("PGUSER", "carddemo"),
                System.getenv().getOrDefault("PGPASSWORD", "carddemo"));
    }

    static void run(Path input, Path out, String url, String user, String password) throws Exception {
        String schema = "read_masters_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        var dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setSchema(schema);
        admin.execute("CREATE SCHEMA " + schema);
        try (var context = new SpringApplicationBuilder(ReadMastersCandidate.class)
                .web(WebApplicationType.NONE)
                .initializers(ctx -> ctx.getBeanFactory().registerSingleton("dataSource", dataSource))
                .run("--spring.batch.job.enabled=false", "--spring.batch.jdbc.initialize-schema=always",
                        "--spring.flyway.default-schema=" + schema, "--spring.main.banner-mode=off")) {
            var missing = EnumSet.noneOf(ReadMaster.class);
            try (var connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                var transfer = new VsamTransfer(connection);
                for (var master : ReadMaster.values()) {
                    Path file = input.resolve("AWS.M2.CARDDEMO." + master.dataset.inputName + ".PS.txt");
                    if (Files.notExists(file)) missing.add(master);
                    else transfer.load(master.dataset, file);
                }
                connection.commit();
            }
            var codes = new TreeMap<String, Map<String, Integer>>();
            var launcher = context.getBean(JobLauncher.class);
            for (var master : ReadMaster.values()) {
                var parameters = new JobParametersBuilder().addString("out", out.toString())
                        .addString("missingInput", Boolean.toString(missing.contains(master))).toJobParameters();
                var execution = launcher.run(context.getBean(master.job.toLowerCase(Locale.ROOT), Job.class), parameters);
                if (execution.getStatus() != BatchStatus.COMPLETED) {
                    throw new IllegalStateException(master.job + " failed: " + execution.getAllFailureExceptions());
                }
                int rc = execution.getStepExecutions().iterator().next().getExecutionContext().getInt("returnCode", 0);
                codes.put(master.job, master == ReadMaster.ACCOUNT ? Map.of("PREDEL", 0, "STEP05", rc)
                        : Map.of("STEP05", rc));
            }
            BatchArtifacts.writeReturnCodes(out, codes);
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
