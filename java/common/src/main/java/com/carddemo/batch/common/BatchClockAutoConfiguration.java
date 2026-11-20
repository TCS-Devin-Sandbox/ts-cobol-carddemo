package com.carddemo.batch.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class BatchClockAutoConfiguration {

    @Bean
    Clock batchClock() {
        return Clock.fixed(Instant.parse("2022-07-05T10:00:00Z"), ZoneOffset.UTC);
    }
}
