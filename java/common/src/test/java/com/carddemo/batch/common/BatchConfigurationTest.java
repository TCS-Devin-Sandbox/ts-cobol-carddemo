package com.carddemo.batch.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class BatchConfigurationTest {

    @Test
    void providesTheFrozenEstateClockWithoutEnablingDocker() {
        try (var context = SpringApplication.run(TestApplication.class)) {
            Clock clock = context.getBean(Clock.class);
            assertThat(clock.instant()).isEqualTo(Instant.parse("2022-07-05T10:00:00Z"));
            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isNull();
        }
    }

    @Test
    void dockerProfileTargetsTheComposeDatabaseService() {
        try (var context = SpringApplication.run(TestApplication.class, "--spring.profiles.active=docker")) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://db:5432/carddemo");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
