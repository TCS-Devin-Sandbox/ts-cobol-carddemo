package com.carddemo.interest.job;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.carddemo.interest.batch.InterestBatchApplication;
import com.carddemo.interest.handoff.HandoffCoordinator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = InterestBatchApplication.class)
class ClosePackagingFailureIT {
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine");
    static { PG.start(); }
    static final Path REPO = Path.of("../../..").toAbsolutePath().normalize();
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("interest.close.jdbc-url", PG::getJdbcUrl);
        r.add("interest.close.db-user", PG::getUsername);
        r.add("interest.close.db-password", PG::getPassword);
        r.add("interest.close.database", () -> "close_packaging_failure_it");
    }
    @Autowired Job interestJob;
    @Autowired JobLauncher launcher;
    @Autowired JdbcTemplate jdbc;
    @SpyBean HandoffCoordinator handoff;
    @TempDir Path temp;

    @Test void TN_01_INT_R03_INT_R10_packagingFailurePreservesCompletedSysoutForBaselineFallbackAndEmptyInput() throws Exception {
        for (String c : List.of("i01-baseline", "i04-default-group-fallback", "empty")) {
            Path input = REPO.resolve("fixtures/interest/" + (c.equals("empty") ? "i01-baseline" : c) + "/input");
            if (c.equals("empty")) {
                Path copy = temp.resolve("empty-input");
                Files.createDirectories(copy);
                try (var files = Files.list(input)) {
                    for (Path file : files.toList()) Files.copy(file, copy.resolve(file.getFileName()));
                }
                Files.write(copy.resolve("AWS.M2.CARDDEMO.TCATBALF.PS.txt"), new byte[0]);
                input = copy;
            }
            byte[] expected = c.equals("empty")
                    ? "START OF EXECUTION OF PROGRAM CBACT04C\nEND OF EXECUTION OF PROGRAM CBACT04C\n".getBytes(StandardCharsets.US_ASCII)
                    : Files.readAllBytes(REPO.resolve("fixtures/interest/" + c + "/expected").resolve(CloseExporter.SYSOUT));
            Path output = temp.resolve(c);
            AtomicInteger attempts = new AtomicInteger();
            doAnswer(invocation -> {
                UUID run = invocation.getArgument(0, UUID.class);
                assertEquals("COMPUTED", jdbc.queryForObject("SELECT state FROM interest.run WHERE run_id=?", String.class, run));
                assertArrayEquals(expected, Files.readAllBytes(output.resolve(CloseExporter.SYSOUT)));
                attempts.incrementAndGet();
                throw new IllegalStateException("injected-package-failure");
            }).when(handoff).handoff(any());
            var execution = launcher.run(interestJob, new JobParametersBuilder()
                    .addString("attempt", UUID.randomUUID().toString()).addString("input", input.toString())
                    .addString("output", output.toString()).toJobParameters());
            assertEquals(1, attempts.get());
            assertEquals(List.of("candidate.load-calculate-private-export", "candidate.export-packaging"),
                    execution.getStepExecutions().stream().map(step -> step.getStepName()).toList());
            assertEquals("12", execution.getExitStatus().getExitCode());
            assertTrue(Files.readString(output.resolve("pilot-failure.json")).contains("TARGET_PACKAGE_HELD"));
            assertTrue(Files.readString(output.resolve("rc.json")).contains("\"STEP15\":12"));
            assertFalse(Files.exists(output.resolve(CloseExporter.ACCOUNT)));
            assertFalse(Files.exists(output.resolve(CloseExporter.SYSTRAN)));
            assertArrayEquals(expected, Files.readAllBytes(output.resolve(CloseExporter.SYSOUT)),
                    "Packaging failure must preserve completed computation SYSOUT: " + c);
        }
    }
}
