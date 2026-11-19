package com.carddemo.interest.pilot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

/** Launches one pilot run and exports it only when every step completed. */
@Service
public class PilotRunService {

    /** Non-zero pilot exit for a failed run; legacy abend RC parity is out of the pilot's scope. */
    public static final int FAILED_RC = 12;
    private static final String RC_FILE = "rc.json";
    private static final String FAILURE_FILE = "pilot-failure.json";
    private static final List<String> ARTIFACTS = List.of("datasets", "sysout", FAILURE_FILE);
    private static final Logger log = LoggerFactory.getLogger(PilotRunService.class);
    private final JobLauncher jobLauncher;
    private final Job interestPilotJob;
    private final PilotExporter exporter;
    private final PilotProperties properties;

    public PilotRunService(JobLauncher jobLauncher, Job interestPilotJob, PilotExporter exporter,
            PilotProperties properties) {
        this.jobLauncher = jobLauncher;
        this.interestPilotJob = interestPilotJob;
        this.exporter = exporter;
        this.properties = properties;
    }

    public PilotRunResult run(Path inputDir, Path outputDir) throws Exception {
        UUID runId = UUID.randomUUID();
        JobParameters parameters = new JobParametersBuilder()
                .addString("runId", runId.toString())
                .addString("inputDir", inputDir.toAbsolutePath().toString())
                .addString("parm", properties.parm())
                .toJobParameters();
        JobExecution execution = jobLauncher.run(interestPilotJob, parameters);
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            if (outputDir != null) {
                Path staging = staging(outputDir, runId);
                try {
                    exporter.export(runId, staging);
                    publish(staging, outputDir);
                } catch (IOException | RuntimeException e) {
                    log.warn("event=run-failed run={} category=EXPORT_FAILED detail={}", runId,
                            e.getClass().getSimpleName());
                    deleteTree(staging);
                    publishFailure(outputDir, runId, "EXPORT_FAILED", null);
                    return new PilotRunResult(runId, BatchStatus.FAILED, FAILED_RC, e);
                }
            }
            log.info("event=run-completed run={} rc=0", runId);
            return new PilotRunResult(runId, execution.getStatus(), 0, null);
        }
        Throwable cause = execution.getAllFailureExceptions().stream().findFirst().orElse(null);
        String category = cause instanceof PilotFailure failure ? failure.category() : "UNEXPECTED";
        // S14: PilotFailure messages carry only dd/op/status/record numbers; other exceptions log their type only.
        log.warn("event=run-failed run={} category={} detail={}", runId, category,
                cause instanceof PilotFailure ? cause.getMessage()
                        : cause == null ? "none" : cause.getClass().getSimpleName());
        if (outputDir != null) {
            publishFailure(outputDir, runId, category, cause);
        }
        return new PilotRunResult(runId, execution.getStatus(), FAILED_RC, cause);
    }

    private static Path staging(Path outputDir, UUID runId) {
        return outputDir.toAbsolutePath().resolve(".staging-" + runId);
    }

    private static void publishFailure(Path outputDir, UUID runId, String category, Throwable cause)
            throws IOException {
        Path staging = staging(outputDir, runId);
        Files.createDirectories(staging);
        String detail = cause instanceof PilotFailure ? cause.getMessage().replace("\"", "'") : "";
        Files.writeString(staging.resolve(FAILURE_FILE), """
                {"run": "%s", "category": "%s", "detail": "%s"}
                """.formatted(runId, category, detail), StandardCharsets.UTF_8);
        PilotExporter.writeReturnCode(staging, FAILED_RC);
        publish(staging, outputDir);
    }

    /**
     * Replaces this run's artifacts in {@code outputDir}: the previous rc.json goes first and the new one
     * arrives last, so no reader sees datasets from one run under another run's return code.
     */
    private static void publish(Path staging, Path outputDir) throws IOException {
        Files.deleteIfExists(outputDir.resolve(RC_FILE));
        for (String artifact : ARTIFACTS) {
            deleteTree(outputDir.resolve(artifact));
        }
        for (String artifact : ARTIFACTS) {
            if (Files.exists(staging.resolve(artifact))) {
                Files.move(staging.resolve(artifact), outputDir.resolve(artifact), StandardCopyOption.ATOMIC_MOVE);
            }
        }
        Files.move(staging.resolve(RC_FILE), outputDir.resolve(RC_FILE), StandardCopyOption.ATOMIC_MOVE);
        deleteTree(staging);
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    public record PilotRunResult(UUID runId, BatchStatus status, int exitCode, Throwable failure) {
    }
}
