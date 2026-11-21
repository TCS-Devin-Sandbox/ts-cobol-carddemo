package com.carddemo.batch.trantype;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@Testcontainers
class TransactionTypeUpdateTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate db;

    @TempDir
    Path directory;

    @BeforeAll
    static void createTables() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new ResourceDatabasePopulator(new FileSystemResource("../../db/postgres/01-transaction-type.sql"))
                .execute(dataSource);
        db = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void seed() {
        db.execute("TRUNCATE carddemo.transaction_type CASCADE");
        db.update("INSERT INTO carddemo.transaction_type VALUES ('01', 'Purchase')");
    }

    @Test
    void duplicateAbortsAllWritesButTheJobProcessesTheRemainingRecords() throws Exception {
        Path input = Files.writeString(directory.resolve("input"), "A07Before error\nA01Duplicate\nX08Invalid\nA08After error\n");
        Path sysout = directory.resolve("sysout");

        assertThat(new TransactionTypeUpdate(dataSource).run(input, sysout)).isEqualTo(4);
        assertThat(db.queryForList("SELECT tr_type FROM carddemo.transaction_type", String.class)).containsExactly("01");
        assertThat(Files.readAllLines(sysout)).containsSubsequence(
                "RECORD INSERTED SUCCESSFULLY",
                "Error accessing: TRANSACTION_TYPE table. SQLCODE: -403" + " ".repeat(26),
                "ERROR: TYPE NOT VALIDSACTION_TYPE table. SQLCODE: -403" + " ".repeat(26),
                "Error accessing: TRANSACTION_TYPE table. SQLCODE: -400" + " ".repeat(26),
                "DSNRUN: COMMIT AT END OF TASK");
    }

    @Test
    void missingUpdateSucceedsAndInvalidOperationDoesNotAbortTheTransaction() throws Exception {
        Path input = Files.writeString(directory.resolve("input"), "U09Absent\nX08Invalid\nU01Revised\nA02"
                + "X".repeat(51) + "\n");
        Path sysout = directory.resolve("sysout");

        assertThat(new TransactionTypeUpdate(dataSource).run(input, sysout)).isEqualTo(4);
        assertThat(db.queryForList("SELECT tr_description FROM carddemo.transaction_type ORDER BY tr_type", String.class))
                .containsExactly("Revised" + " ".repeat(43), "X".repeat(50));
        assertThat(Files.readAllLines(sysout)).containsSubsequence(
                "RECORD UPDATED SUCCESSFULLY", "ERROR: TYPE NOT VALID" + " ".repeat(59),
                "RECORD UPDATED SUCCESSFULLY", "RECORD INSERTED SUCCESSFULLY");
    }

    @Test
    void batchFailureAfterSqlDoesNotCommitAndTheSameInstanceCanRetry() throws Exception {
        Path input = Files.writeString(directory.resolve("input"), "A07New type\n");
        Path blocker = Files.createDirectory(directory.resolve("rc.json"));
        new ResourceDatabasePopulator(new ClassPathResource("org/springframework/batch/core/schema-postgresql.sql"))
                .execute(dataSource);
        try (var context = new SpringApplicationBuilder(TrantypeBatchApplication.class).web(WebApplicationType.NONE)
                .initializers(ctx -> ctx.getBeanFactory().registerSingleton("dataSource", dataSource))
                .run("--spring.batch.job.enabled=false", "--spring.batch.jdbc.initialize-schema=never")) {
            var launcher = context.getBean(JobLauncher.class);
            var job = context.getBean("mnttrdb2", Job.class);
            var parameters = new JobParametersBuilder().addString("input", input.toString())
                    .addString("out", directory.toString()).addString("run.id", "restart").toJobParameters();
            var failed = launcher.run(job, parameters);
            assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(db.queryForList("SELECT tr_type FROM carddemo.transaction_type", String.class)).containsExactly("01");
            Files.delete(blocker);
            var restarted = launcher.run(job, parameters);
            assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(restarted.getJobInstance().getId()).isEqualTo(failed.getJobInstance().getId());
            assertThat(db.queryForList("SELECT tr_type FROM carddemo.transaction_type ORDER BY tr_type", String.class))
                    .containsExactly("01", "07");
        }
    }

    @Test
    void failingFollowUpRollsBackDynamicSqlInTheSameTransaction() throws Exception {
        Path sql = Files.writeString(directory.resolve("sql"), "-- ignored\n"
                + "%-72signored".formatted("UPDATE transaction_type SET tr_description = 'Revised'"));
        Path input = Files.writeString(directory.resolve("input"), "A01Duplicate\n");
        Path sysout = directory.resolve("sysout");

        assertThat(new DynamicTransactionTypeUpdate(dataSource).run(sql, input, "COBTUPDT", sysout)).isEqualTo(4);
        assertThat(db.queryForObject("SELECT tr_description FROM carddemo.transaction_type", String.class))
                .isEqualTo("Purchase");
        assertThat(Files.readAllLines(sysout)).containsSubsequence(
                "CBTRNDYN: 01 SQL LINES READ",
                "CBTRNDYN: EXECUTE SQLCODE +0000000000",
                "CBTRNDYN: CALLING COBTUPDT",
                "Error accessing: TRANSACTION_TYPE table. SQLCODE: -403" + " ".repeat(26));
    }

    @Test
    void emptySqlWithoutFollowUpReturnsDynamicJobRc() throws Exception {
        Path sql = Files.writeString(directory.resolve("sql"), "-- no statement\n");
        Path sysout = directory.resolve("sysout");

        assertThat(new DynamicTransactionTypeUpdate(dataSource).run(sql, null, "", sysout)).isEqualTo(8);
        assertThat(Files.readAllLines(sysout)).containsSubsequence(
                "CBTRNDYN: 00 SQL LINES READ", "CBTRNDYN: EXECUTE SQLCODE -0000000212", "CBTRNDYN: END");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-- operator note", "/* operator note */"})
    void executesMaintenanceWithTrailingComment(String comment) throws Exception {
        Path sql = Files.writeString(directory.resolve("sql"),
                "DELETE FROM transaction_type WHERE tr_type = '01'; " + comment);
        Path input = Files.writeString(directory.resolve("input"), "A02Replacement\n");
        Path sysout = directory.resolve("sysout");

        assertThat(new DynamicTransactionTypeUpdate(dataSource).run(sql, input, "COBTUPDT", sysout)).isZero();
        assertThat(db.queryForList("SELECT tr_type FROM carddemo.transaction_type", String.class)).containsExactly("02");
    }

    @Test
    void dynamicSqlCannotIssueATransactionControlCommand() throws Exception {
        Path sql = Files.writeString(directory.resolve("sql"), "DELETE FROM transaction_type; COMMIT\n");
        Path sysout = directory.resolve("sysout");

        assertThatIllegalArgumentException().isThrownBy(
                () -> new DynamicTransactionTypeUpdate(dataSource).run(sql, null, "", sysout));
        assertThat(db.queryForObject("SELECT count(*) FROM carddemo.transaction_type", Integer.class)).isEqualTo(1);
    }
}
