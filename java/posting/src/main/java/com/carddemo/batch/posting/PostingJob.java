package com.carddemo.batch.posting;

import java.time.Clock;
import javax.sql.DataSource;

import com.carddemo.batch.common.StepCheckpoint;
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
    Job posting(JobRepository repository, PlatformTransactionManager transactions, JdbcTemplate jdbc, Clock clock,
                DataSource dataSource) {
        var step15 = new StepBuilder("POSTTRAN.STEP15", repository)
                .tasklet(StepCheckpoint.once(new PostingTasklet(new PostingStore(jdbc), clock)), transactions)
                .build();
        var chain = new JobBuilder("posting", repository).start(step15);
        for (var step : TransactionReportJob.steps(repository, transactions, dataSource, jdbc)) {
            chain.next(step);
        }
        return chain.build();
    }
}
