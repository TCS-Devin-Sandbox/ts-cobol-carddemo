package com.carddemo.interest.batch;

import com.carddemo.interest.handoff.HandoffCoordinator;
import com.carddemo.interest.handoff.HandoffOutcome;
import com.carddemo.interest.handoff.HostBridge;
import com.carddemo.interest.handoff.PacketBuilder;
import com.carddemo.interest.handoff.PublicationLedger;
import com.carddemo.interest.job.CloseExporter;
import com.carddemo.interest.job.CloseRunService;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class InterestJobConfig {
    @Bean HandoffCoordinator shadowHandoff(DataSource source, PlatformTransactionManager manager) {
        var jdbc = new JdbcTemplate(source);
        HostBridge host = (HostBridge) Proxy.newProxyInstance(HostBridge.class.getClassLoader(), new Class<?>[]{HostBridge.class},
                (proxy, method, args) -> { throw new IllegalStateException("Shadow must not call host"); });
        return new HandoffCoordinator(new PacketBuilder(jdbc), new PublicationLedger(jdbc, new TransactionTemplate(manager)),
                host, (run, generation) -> { throw new IllegalStateException("Shadow must not signal release"); });
    }

    @Bean Job interestJob(JobRepository repository, PlatformTransactionManager manager, CloseRunService service,
            CloseExporter exporter, HandoffCoordinator handoff, Environment e, DataSource source) {
        var noTransaction = new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        var compute = new StepBuilder("candidate.load-calculate-private-export", repository).tasklet((contribution, chunk) -> {
            var execution = contribution.getStepExecution().getJobExecution();
            var parameters = execution.getJobParameters();
            String resume = parameters.getString("resume");
            var identity = new CloseRunService.Identity(e.getRequiredProperty("interest.close.parm"),
                    e.getRequiredProperty("interest.close.cycle"), e.getRequiredProperty("interest.close.feed"),
                    e.getRequiredProperty("interest.close.build-revision"), e.getRequiredProperty("interest.close.rule-revision"),
                    e.getRequiredProperty("interest.close.clock") + "/" + e.getRequiredProperty("interest.close.zone"));
            var result = service.run(Path.of(parameters.getString("input")), Path.of(parameters.getString("output")),
                    identity, resume == null ? null : UUID.fromString(resume));
            var context = execution.getExecutionContext();
            context.putString("run", result.runId().toString());
            if (result.snapshotId() != null) context.putString("snapshot", result.snapshotId().toString());
            context.putInt("rc", result.rc());
            contribution.setExitStatus(new ExitStatus(Integer.toString(result.rc())));
            return RepeatStatus.FINISHED;
        }, manager).transactionAttribute(noTransaction).build();
        var packaging = new StepBuilder("candidate.export-packaging", repository).tasklet((contribution, chunk) -> {
            var execution = contribution.getStepExecution().getJobExecution();
            var context = execution.getExecutionContext();
            UUID run = UUID.fromString(context.getString("run"));
            try {
                if (!(handoff.handoff(run) instanceof HandoffOutcome.ShadowRehearsed shadow))
                    throw new IllegalStateException("Expected shadow rehearsal");
                Long records = new JdbcTemplate(source).queryForObject(
                        "SELECT record_count FROM interest.publication WHERE run_id=?", Long.class, run);
                LoggerFactory.getLogger(InterestJobConfig.class).info("event=package-shadow run={} records={} payload_sha256={}",
                        run, records, shadow.payloadSha256());
                contribution.setExitStatus(new ExitStatus("0"));
            } catch (Exception failure) {
                context.putInt("rc", 12);
                exporter.export(run, UUID.fromString(context.getString("snapshot")),
                        Path.of(execution.getJobParameters().getString("output")), 12, "TARGET_PACKAGE_HELD");
                LoggerFactory.getLogger(InterestJobConfig.class).warn("event=package-held run={} category=TARGET_PACKAGE_HELD", run);
                contribution.setExitStatus(new ExitStatus("12"));
            }
            return RepeatStatus.FINISHED;
        }, manager).transactionAttribute(noTransaction).build();
        return new JobBuilder("interestCandidate", repository).listener(new JobExecutionListener() {
            @Override public void afterJob(JobExecution execution) {
                int rc = execution.getStatus() == org.springframework.batch.core.BatchStatus.COMPLETED
                        ? execution.getExecutionContext().getInt("rc", 12) : 12;
                if (rc != 0) execution.setStatus(org.springframework.batch.core.BatchStatus.FAILED);
                execution.setExitStatus(new ExitStatus(Integer.toString(rc)));
            }
        }).start(compute).on("0").to(packaging).on("*").end()
                .from(compute).on("*").end().end().build();
    }
}
