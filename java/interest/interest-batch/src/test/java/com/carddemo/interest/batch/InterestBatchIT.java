package com.carddemo.interest.batch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.carddemo.interest.handoff.HandoffCoordinator;
import com.carddemo.interest.job.CloseExporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = InterestBatchApplication.class)
class InterestBatchIT {
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine");
    static { PG.start(); }
    static final Path REPO = Path.of("../../..").toAbsolutePath().normalize();
    static final Path I01 = REPO.resolve("fixtures/interest/i01-baseline/input");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("interest.close.jdbc-url", PG::getJdbcUrl);
        r.add("interest.close.db-user", PG::getUsername);
        r.add("interest.close.db-password", PG::getPassword);
        r.add("interest.close.database", () -> "interest_candidate_it");
    }
    @Autowired Job interestJob;
    @Autowired JobLauncher launcher;
    @Autowired JdbcTemplate jdbc;
    @SpyBean HandoffCoordinator handoff;
    @TempDir Path temp;

    JobExecution run(Path input, Path output) throws Exception {
        return launcher.run(interestJob, new JobParametersBuilder().addString("attempt", UUID.randomUUID().toString())
                .addString("input", input.toString()).addString("output", output.toString()).toJobParameters());
    }
    UUID runId(JobExecution execution) { return UUID.fromString(execution.getExecutionContext().getString("run")); }
    int publications(JobExecution execution) {
        return jdbc.queryForObject("SELECT count(*) FROM interest.publication WHERE run_id=?", Integer.class, runId(execution));
    }
    void storedStatus(JobExecution execution, String status, String exitCode) {
        assertEquals(status, execution.getStatus().name());
        var stored = jdbc.queryForMap("SELECT status, exit_code FROM interest_batch.BATCH_JOB_EXECUTION WHERE job_execution_id=?", execution.getId());
        assertEquals(status, stored.get("status"));
        assertEquals(exitCode, stored.get("exit_code"));
    }
    void failedArtifacts(Path output) throws Exception {
        assertTrue(Files.readString(output.resolve("rc.json")).contains("\"maxrc\":12"));
        assertFalse(Files.exists(output.resolve(CloseExporter.ACCOUNT)));
        assertFalse(Files.exists(output.resolve(CloseExporter.SYSTRAN)));
    }

    @Test void INT_H06_i01_computesThenPackagesPrivately() throws Exception {
        var execution = run(I01, temp);
        assertEquals("0", execution.getExitStatus().getExitCode());
        storedStatus(execution, "COMPLETED", "0");
        assertEquals(List.of("candidate.load-calculate-private-export", "candidate.export-packaging"),
                execution.getStepExecutions().stream().map(s -> s.getStepName()).toList());
        for (String artifact : List.of(CloseExporter.ACCOUNT, CloseExporter.SYSTRAN, CloseExporter.SYSOUT)) {
            assertArrayEquals(Files.readAllBytes(REPO.resolve("fixtures/interest/i01-baseline/expected").resolve(artifact)),
                    Files.readAllBytes(temp.resolve(artifact)));
        }
        assertTrue(Files.readString(temp.resolve("rc.json")).contains("\"maxrc\":0"));
        assertEquals(1, publications(execution));
        assertEquals("PRIVATE", jdbc.queryForObject("SELECT state FROM interest.publication WHERE run_id=?", String.class, runId(execution)));
        assertTrue(jdbc.queryForObject("SELECT destination FROM interest.publication WHERE run_id=?", String.class, runId(execution)).startsWith("shadow:"));
    }

    @Test void INT_R03_missingDefaultSkipsPackaging() throws Exception {
        var execution = run(Path.of("src/test/resources/pilot/specimens/missing-default-rate").toAbsolutePath(), temp);
        assertEquals("12", execution.getExitStatus().getExitCode());
        storedStatus(execution, "FAILED", "12");
        assertEquals(1, execution.getStepExecutions().size());
        assertEquals(0, publications(execution));
        failedArtifacts(temp);
    }

    @Test void INT_H06_packageRejectionRemovesSuccessArtifacts() throws Exception {
        doAnswer(invocation -> {
            jdbc.update("UPDATE interest.interest_tx SET amount=amount+1 WHERE run_id=?", invocation.getArgument(0, UUID.class));
            return invocation.callRealMethod();
        }).when(handoff).handoff(any());
        var execution = run(I01, temp);
        assertEquals("12", execution.getExitStatus().getExitCode());
        storedStatus(execution, "FAILED", "12");
        assertEquals(0, publications(execution));
        failedArtifacts(temp);
        assertTrue(Files.readString(temp.resolve("pilot-failure.json")).contains("TARGET_PACKAGE_HELD"));
    }

    @Test void INT_R09_emptyInputPackagesZeroRecords() throws Exception {
        Path input = temp.resolve("input");
        Files.createDirectories(input);
        try (var files = Files.list(I01)) {
            for (Path file : files.toList()) Files.copy(file, input.resolve(file.getFileName()));
        }
        Files.write(input.resolve("AWS.M2.CARDDEMO.TCATBALF.PS.txt"), new byte[0]);
        Path output = temp.resolve("output");
        var execution = run(input, output);
        assertEquals("0", execution.getExitStatus().getExitCode());
        assertEquals(1, publications(execution));
        assertEquals(0L, jdbc.queryForObject("SELECT record_count FROM interest.publication WHERE run_id=?", Long.class, runId(execution)));
        assertEquals(0, Files.size(output.resolve(CloseExporter.SYSTRAN)));
    }
}
