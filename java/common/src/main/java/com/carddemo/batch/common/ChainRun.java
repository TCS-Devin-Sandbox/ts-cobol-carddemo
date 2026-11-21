package com.carddemo.batch.common;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.HexFormat;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.DefaultJobKeyGenerator;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class ChainRun {
    private ChainRun() { }

    @FunctionalInterface
    public interface Staging {
        void load(Connection connection) throws Exception;
    }

    @FunctionalInterface
    public interface Export {
        void write(DataSource dataSource, JobRepository repository, JobExecution execution) throws Exception;
    }

    public static JobParametersBuilder parameters(Path input, Path out) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var files = Files.list(input)) {
            for (var file : files.filter(path -> path.getFileName().toString().endsWith(".txt")).sorted().toList()) {
                digest.update(file.getFileName().toString().getBytes(UTF_8));
                digest.update((byte) 0);
                var contents = MessageDigest.getInstance("SHA-256");
                try (var stream = new DigestInputStream(Files.newInputStream(file), contents)) {
                    stream.transferTo(OutputStream.nullOutputStream());
                }
                digest.update(contents.digest());
            }
        }
        return new JobParametersBuilder()
                .addString("input", input.toAbsolutePath().normalize().toString())
                .addString("out", out.toAbsolutePath().normalize().toString())
                .addString("inputDigest", HexFormat.of().formatHex(digest.digest()));
    }

    public static void run(Class<?> application, String chain, String runId, String url, String user, String password,
                           JobParameters parameters, Staging staging, Export export) throws Exception {
        if (runId != null && !runId.matches("[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("run-id must be 1-40 lowercase letters, digits or underscores");
        }
        boolean persistent = runId != null;
        String schema = chain + "_" + (persistent ? runId : UUID.randomUUID().toString().replace("-", ""));
        var dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setSchema(schema);

        try (var lock = new DriverManagerDataSource(url, user, password).getConnection();
             var acquire = lock.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?, 0))");
             var sql = lock.createStatement()) {
            acquire.setString(1, schema);
            try (var result = acquire.executeQuery()) {
                if (!result.next() || !result.getBoolean(1)) {
                    throw new IllegalStateException("Chain run already active: " + schema);
                }
            }
            sql.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            try {
                try (var context = new SpringApplicationBuilder(application).web(WebApplicationType.NONE)
                        .initializers(ctx -> ctx.getBeanFactory().registerSingleton("dataSource", dataSource))
                        .run("--spring.batch.job.enabled=false", "--spring.batch.jdbc.initialize-schema=never",
                                "--spring.flyway.default-schema=" + schema, "--spring.main.banner-mode=off")) {
                    initializeRepository(dataSource);
                    stage(dataSource, parameters, staging);
                    launch(dataSource, context, parameters, export);
                }
            } finally {
                if (!persistent) sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private static void initializeRepository(DataSource dataSource) throws Exception {
        if (Boolean.TRUE.equals(new JdbcTemplate(dataSource)
                .queryForObject("SELECT to_regclass('batch_job_instance') IS NOT NULL", Boolean.class))) return;
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("org/springframework/batch/core/schema-postgresql.sql"));
            connection.commit();
        }
    }

    private static void stage(DataSource dataSource, JobParameters parameters, Staging staging) throws Exception {
        String identity = new DefaultJobKeyGenerator().generateKey(parameters);
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            connection.setAutoCommit(false);
            sql.execute("CREATE TABLE IF NOT EXISTS carddemo_run (identity text NOT NULL)");
            try (var row = sql.executeQuery("SELECT identity FROM carddemo_run")) {
                if (row.next()) {
                    if (!identity.equals(row.getString(1))) {
                        throw new IllegalArgumentException("Run parameters or input contents changed; use a new run-id");
                    }
                    return;
                }
            }
            staging.load(connection);
            try (var insert = connection.prepareStatement("INSERT INTO carddemo_run VALUES (?)")) {
                insert.setString(1, identity);
                insert.executeUpdate();
            }
            connection.commit();
        }
    }

    private static void launch(DataSource dataSource, ApplicationContext context, JobParameters parameters, Export export)
            throws Exception {
        var repository = context.getBean(JobRepository.class);
        var job = context.getBean(Job.class);
        var execution = repository.getLastJobExecution(job.getName(), parameters);
        if (execution == null || execution.getStatus() != BatchStatus.COMPLETED) {
            execution = context.getBean(JobLauncher.class).run(job, parameters);
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                var failures = execution.getAllFailureExceptions();
                throw new IllegalStateException(job.getName() + " failed: " + execution.getStatus(),
                        failures.isEmpty() ? null : failures.getFirst());
            }
        }
        export.write(dataSource, repository, execution);
    }
}
