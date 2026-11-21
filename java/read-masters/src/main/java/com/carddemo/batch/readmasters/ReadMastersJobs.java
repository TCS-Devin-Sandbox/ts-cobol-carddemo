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
    Job readacct(JobRepository repository, PlatformTransactionManager transactions, JdbcTemplate jdbc) {
        return job(ReadMaster.ACCOUNT, repository, transactions, jdbc);
    }

    @Bean
    Job readcard(JobRepository repository, PlatformTransactionManager transactions, JdbcTemplate jdbc) {
        return job(ReadMaster.CARD, repository, transactions, jdbc);
    }

    @Bean
    Job readxref(JobRepository repository, PlatformTransactionManager transactions, JdbcTemplate jdbc) {
        return job(ReadMaster.XREF, repository, transactions, jdbc);
    }

    @Bean
    Job readcust(JobRepository repository, PlatformTransactionManager transactions, JdbcTemplate jdbc) {
        return job(ReadMaster.CUSTOMER, repository, transactions, jdbc);
    }

    private Job job(ReadMaster master, JobRepository repository, PlatformTransactionManager transactions,
                    JdbcTemplate jdbc) {
        var step = new StepBuilder("STEP05", repository)
                .tasklet(new ReadMasterTasklet(master, new ReadMasterStore(jdbc)), transactions).build();
        var builder = new JobBuilder(master.job, repository).preventRestart();
        if (master == ReadMaster.ACCOUNT) {
            var predel = new StepBuilder("PREDEL", repository).tasklet((contribution, context) -> {
                Path datasets = Path.of(contribution.getStepExecution().getJobParameters().getString("out"))
                        .resolve("datasets");
                for (String suffix : new String[]{"PSCOMP", "ARRYPS", "VBPS"}) {
                    Files.deleteIfExists(datasets.resolve("AWS.M2.CARDDEMO.ACCTDATA." + suffix + ".txt"));
                }
                return RepeatStatus.FINISHED;
            }, transactions).build();
            return builder.start(predel).next(step).build();
        }
        return builder.start(step).build();
    }
}
