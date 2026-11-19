package com.carddemo.interest.pilot;

import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Job {@code interestPilot}: snapshot intake, then the calculation step (INT-ARCH A1/A3). */
@Configuration
public class PilotJobConfig {

    public static final String JOB_NAME = "interestPilot";

    @Bean
    Clock pilotClock(PilotProperties properties) {
        return properties.clock() == null
                ? Clock.system(properties.zone())
                : Clock.fixed(properties.clock().atZone(properties.zone()).toInstant(), properties.zone());
    }

    @Bean
    LegacyTimestamp legacyTimestamp(Clock pilotClock) {
        return new LegacyTimestamp(pilotClock);
    }

    @Bean
    InterestCalculator interestCalculator() {
        return new InterestCalculator();
    }

    @Bean
    Job interestPilotJob(JobRepository jobRepository, Step interestPilotIntake, Step interestPilotStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(interestPilotIntake)
                .next(interestPilotStep)
                .build();
    }

    @Bean
    Step interestPilotIntake(JobRepository jobRepository, PlatformTransactionManager tx, SnapshotIntake intake) {
        return new StepBuilder("interestPilotIntake", jobRepository).tasklet(intake, tx).build();
    }

    @Bean
    Step interestPilotStep(JobRepository jobRepository, PlatformTransactionManager tx, PilotProperties properties,
            AccountGroupReader reader, InterestGroupProcessor processor, GroupOutcomeWriter writer) {
        return new StepBuilder("interestPilotStep", jobRepository)
                .<AccountGroup, GroupOutcome>chunk(properties.groupsPerTransaction(), tx)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    @StepScope
    SnapshotIntake snapshotIntake(PilotStore store, @Value("#{jobParameters['runId']}") String runId,
            @Value("#{jobParameters['inputDir']}") String inputDir) {
        return new SnapshotIntake(store, UUID.fromString(runId), Path.of(inputDir));
    }

    @Bean
    @StepScope
    AccountGroupReader accountGroupReader(PilotStore store, @Value("#{jobParameters['runId']}") String runId) {
        return new AccountGroupReader(store, UUID.fromString(runId));
    }

    @Bean
    @StepScope
    InterestGroupProcessor interestGroupProcessor(PilotStore store, InterestCalculator calculator,
            LegacyTimestamp legacyTimestamp, @Value("#{jobParameters['parm']}") String parm,
            @Value("#{jobParameters['runId']}") String runId) {
        return new InterestGroupProcessor(store, calculator, legacyTimestamp, new TransactionIdSource(parm),
                UUID.fromString(runId));
    }

    @Bean
    @StepScope
    GroupOutcomeWriter groupOutcomeWriter(PilotStore store, @Value("#{jobParameters['runId']}") String runId) {
        return new GroupOutcomeWriter(store, UUID.fromString(runId));
    }
}
