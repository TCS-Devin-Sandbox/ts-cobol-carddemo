package com.carddemo.batch.trantype;

import com.carddemo.batch.common.BatchArtifacts;
import java.nio.file.Path;
import java.util.Map;
import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.DefaultJobParametersValidator;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = FlywayAutoConfiguration.class)
public class TrantypeBatchApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(TrantypeBatchApplication.class, args)));
    }

    @Bean
    Job mnttrdb2(JobRepository repository, DataSource dataSource) {
        var step = new StepBuilder("STEP1", repository)
                // COBTUPDT's transaction is separate from the Spring Batch metadata writes.
                .tasklet((contribution, context) -> {
                    var parameters = context.getStepContext().getJobParameters();
                    Path input = Path.of((String) parameters.get("input"));
                    Path out = Path.of((String) parameters.get("out"));
                    int rc = new TransactionTypeUpdate(dataSource).run(input, out.resolve("sysout/MNTTRDB2.STEP1.txt"));
                    BatchArtifacts.writeReturnCodes(out, Map.of("MNTTRDB2", Map.of("STEP1", rc)));
                    return RepeatStatus.FINISHED;
                }, new ResourcelessTransactionManager())
                .build();
        return new JobBuilder("MNTTRDB2", repository)
                .validator(new DefaultJobParametersValidator(new String[]{"input", "out"}, new String[]{"run.id"}))
                .start(step)
                .build();
    }
}
