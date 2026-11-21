package com.carddemo.batch.readmasters;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
class ReadMastersJobs {
    @Bean
    Job readMasters(JobRepository repository, PlatformTransactionManager transactions, JdbcTemplate jdbc) {
        var predel = new StepBuilder("READACCT.PREDEL", repository).tasklet((contribution, context) -> {
            Path datasets = Path.of(contribution.getStepExecution().getJobParameters().getString("out"))
                    .resolve("datasets");
            for (String suffix : new String[]{"PSCOMP", "ARRYPS", "VBPS"}) {
                Files.deleteIfExists(datasets.resolve("AWS.M2.CARDDEMO.ACCTDATA." + suffix + ".txt"));
            }
            return RepeatStatus.FINISHED;
        }, transactions).build();
        var chain = new JobBuilder("read-masters", repository).start(predel);
        for (var master : ReadMaster.values()) {
            chain.next(new StepBuilder(master.job + ".STEP05", repository)
                    .tasklet(new ReadMasterTasklet(master, new ReadMasterStore(jdbc)), transactions).build());
        }
        return chain.build();
    }
}
