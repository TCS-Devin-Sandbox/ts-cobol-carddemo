package com.carddemo.interest.batch;

import com.carddemo.interest.calc.GroupWriter;
import com.carddemo.interest.calc.InterestRules;
import com.carddemo.interest.intake.SnapshotIntake;
import com.carddemo.interest.job.CloseDatabase;
import com.carddemo.interest.job.CloseExporter;
import com.carddemo.interest.job.CloseRunService;
import com.carddemo.interest.job.CloseStore;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({InterestJobConfig.class, CloseStore.class, SnapshotIntake.class, CloseExporter.class, CloseRunService.class})
@PropertySource("classpath:job/close.properties")
public class InterestBatchApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(InterestBatchApplication.class);
        application.setDefaultProperties(Map.of("spring.config.location", "classpath:job/close.properties",
                "logging.level.com.carddemo.interest.batch", "INFO"));
        int rc = 12;
        try (var context = application.run(args)) {
            rc = context.getBean(ExitCode.class).value;
        } catch (Exception failure) {
            org.slf4j.LoggerFactory.getLogger(InterestBatchApplication.class).warn("event=run-refused phase=coordinator category=TARGET_RUN_REFUSED");
        }
        System.exit(rc);
    }

    static final class ExitCode { int value = 12; }
    @Bean ExitCode candidateExitCode() { return new ExitCode(); }
    @Bean DataSource dataSource(Environment e) throws Exception {
        return CloseDatabase.initialize(e.getRequiredProperty("interest.close.jdbc-url"), e.getRequiredProperty("interest.close.db-user"),
                e.getRequiredProperty("interest.close.db-password"), e.getProperty("interest.close.database", "interest_close"));
    }
    @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
    @Bean GroupWriter.CommitHook commitHook() { return new GroupWriter.CommitHook() {}; }
    @Bean Clock closeClock(Environment e) {
        ZoneId zone = ZoneId.of(e.getRequiredProperty("interest.close.zone"));
        return Clock.fixed(LocalDateTime.parse(e.getRequiredProperty("interest.close.clock")).atZone(zone).toInstant(), zone);
    }
    @Bean InterestRules interestRules(Clock clock) { return new InterestRules(clock); }
    @Bean ApplicationRunner candidateRunner(Environment e, JobLauncher launcher, Job interestJob, ExitCode exit) {
        return args -> {
            if (!e.getProperty("interest.close.run-on-startup", Boolean.class, false)) return;
            var parameters = new JobParametersBuilder().addString("attempt", UUID.randomUUID().toString())
                    .addString("input", e.getRequiredProperty("interest.close.input-dir"))
                    .addString("output", e.getRequiredProperty("interest.close.output-dir"));
            String resume = e.getProperty("interest.close.resume-run-id");
            if (resume != null) parameters.addString("resume", resume);
            var execution = launcher.run(interestJob, parameters.toJobParameters());
            exit.value = "0".equals(execution.getExitStatus().getExitCode()) ? 0 : 12;
        };
    }
}
