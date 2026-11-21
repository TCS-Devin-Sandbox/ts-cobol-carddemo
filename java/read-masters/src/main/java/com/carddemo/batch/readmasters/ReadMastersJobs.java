package com.carddemo.batch.readmasters;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
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
        return new JobBuilder(master.job, repository).preventRestart().start(step).build();
    }
}
