package com.carddemo.interest.pilot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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
                exporter.export(runId, outputDir);
            }
            log.info("event=run-completed run={} rc=0", runId);
            return new PilotRunResult(runId, execution.getStatus(), 0, null);
        }
        Throwable cause = execution.getAllFailureExceptions().stream().findFirst().orElse(null);
        String category = cause instanceof PilotFailure failure ? failure.category() : "UNEXPECTED";
        log.warn("event=run-failed run={} category={} detail={}", runId, category,
                cause instanceof PilotFailure ? cause.getMessage() : String.valueOf(cause));
        if (outputDir != null) {
            writeFailure(outputDir, runId, category, cause);
        }
        return new PilotRunResult(runId, execution.getStatus(), FAILED_RC, cause);
    }

    private static void writeFailure(Path outputDir, UUID runId, String category, Throwable cause) throws IOException {
        Files.createDirectories(outputDir);
        String detail = cause instanceof PilotFailure ? cause.getMessage().replace("\"", "'") : "";
        Files.writeString(outputDir.resolve("pilot-failure.json"), """
                {"run": "%s", "category": "%s", "detail": "%s"}
                """.formatted(runId, category, detail), StandardCharsets.UTF_8);
        PilotExporter.writeReturnCode(outputDir, FAILED_RC);
    }

    public record PilotRunResult(UUID runId, BatchStatus status, int exitCode, Throwable failure) {
    }
}
