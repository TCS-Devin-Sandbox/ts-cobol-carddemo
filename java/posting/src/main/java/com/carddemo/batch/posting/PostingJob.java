package com.carddemo.batch.posting;

import java.time.Clock;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
class PostingJob {

    @Bean
    Job posttran(JobRepository repository, PlatformTransactionManager transactions, JdbcTemplate jdbc, Clock clock) {
        var step15 = new StepBuilder("STEP15", repository)
                .tasklet(new PostingTasklet(new PostingStore(jdbc), clock), transactions)
                .build();
        return new JobBuilder("POSTTRAN", repository)
                .preventRestart()
                .start(step15)
                .build();
    }
}
