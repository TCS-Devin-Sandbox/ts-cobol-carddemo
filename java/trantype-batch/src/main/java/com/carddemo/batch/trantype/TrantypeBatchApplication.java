package com.carddemo.batch.trantype;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.StepCheckpoint;
import java.nio.file.Path;
import java.util.Map;
import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.DefaultJobParametersValidator;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootApplication(exclude = FlywayAutoConfiguration.class)
public class TrantypeBatchApplication {

    public static void main(String[] args) {
        var application = new SpringApplication(TrantypeBatchApplication.class);
        application.setDefaultProperties(Map.of("spring.batch.job.name", "MNTTRDB2"));
        System.exit(SpringApplication.exit(application.run(args)));
    }

    @Bean
    Job mnttrdb2(JobRepository repository, DataSource dataSource, PlatformTransactionManager transactions) {
        var step = new StepBuilder("STEP1", repository)
                .tasklet(StepCheckpoint.once((contribution, context) -> {
                    var parameters = context.getStepContext().getJobParameters();
                    Path input = Path.of((String) parameters.get("input"));
                    Path out = Path.of((String) parameters.get("out"));
                    int rc = new TransactionTypeUpdate(dataSource).run(input, out.resolve("sysout/MNTTRDB2.STEP1.txt"));
                    BatchArtifacts.writeReturnCodes(out, Map.of("MNTTRDB2", Map.of("STEP1", rc)));
                    return RepeatStatus.FINISHED;
                }), transactions)
                .build();
        return new JobBuilder("MNTTRDB2", repository)
                .validator(new DefaultJobParametersValidator(new String[]{"input", "out"}, new String[]{"run.id"}))
                .start(step)
                .build();
    }

    @Bean
    Job mnttrdyn(JobRepository repository, DataSource dataSource, PlatformTransactionManager transactions) {
        var step = new StepBuilder("STEP1", repository)
                .tasklet(StepCheckpoint.once((contribution, context) -> {
                    var parameters = context.getStepContext().getJobParameters();
                    Path dynsql = Path.of((String) parameters.get("dynsql"));
                    String inputParameter = (String) parameters.get("input");
                    Path input = inputParameter == null ? null : Path.of(inputParameter);
                    String parm = (String) parameters.getOrDefault("parm", "");
                    Path out = Path.of((String) parameters.get("out"));
                    int rc = new DynamicTransactionTypeUpdate(dataSource)
                            .run(dynsql, input, parm, out.resolve("sysout/MNTTRDYN.STEP1.txt"));
                    BatchArtifacts.writeReturnCodes(out, Map.of("MNTTRDYN", Map.of("STEP1", rc)));
                    return RepeatStatus.FINISHED;
                }), transactions)
                .build();
        return new JobBuilder("MNTTRDYN", repository)
                .validator(new DefaultJobParametersValidator(new String[]{"dynsql", "out"},
                        new String[]{"input", "parm", "run.id"}))
                .start(step)
                .build();
    }
}
