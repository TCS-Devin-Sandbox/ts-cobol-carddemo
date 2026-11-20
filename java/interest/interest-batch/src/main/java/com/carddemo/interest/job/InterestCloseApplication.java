package com.carddemo.interest.job;

import com.carddemo.interest.calc.GroupWriter;
import com.carddemo.interest.calc.InterestRules;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan({"com.carddemo.interest.intake", "com.carddemo.interest.job", "com.carddemo.interest.calc"})
@PropertySource("classpath:job/close.properties")
public class InterestCloseApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(InterestCloseApplication.class);
        application.setDefaultProperties(Map.of("spring.config.location", "classpath:job/close.properties"));
        try (var context = application.run(args)) {
            System.exit(context.getBean(ExitCode.class).value);
        }
    }

    static final class ExitCode { int value; }
    @Bean ExitCode closeExitCode() { return new ExitCode(); }

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

    @Bean ApplicationRunner closeRunner(Environment e, CloseRunService service, ExitCode exit) {
        return args -> {
            if (!e.getProperty("interest.close.run-on-startup", Boolean.class, false)) return;
            String resume = e.getProperty("interest.close.resume-run-id");
            var identity = new CloseRunService.Identity(e.getRequiredProperty("interest.close.parm"),
                    e.getRequiredProperty("interest.close.cycle"), e.getRequiredProperty("interest.close.feed"),
                    e.getRequiredProperty("interest.close.build-revision"), e.getRequiredProperty("interest.close.rule-revision"),
                    e.getRequiredProperty("interest.close.clock") + "/" + e.getRequiredProperty("interest.close.zone"));
            try {
                exit.value = service.run(Path.of(e.getRequiredProperty("interest.close.input-dir")),
                        Path.of(e.getRequiredProperty("interest.close.output-dir")), identity,
                        resume == null ? null : UUID.fromString(resume)).rc();
            } catch (Exception failure) {
                org.slf4j.LoggerFactory.getLogger(InterestCloseApplication.class).warn("event=run-refused phase=coordinator category=TARGET_RUN_REFUSED");
                exit.value = 12;
            }
        };
    }
}
