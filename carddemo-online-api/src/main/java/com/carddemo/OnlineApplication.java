package com.carddemo;

import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OnlineApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlineApplication.class, args);
    }

    @Bean
    FlywayMigrationStrategy migrateOnlineSchemas() {
        return transactionTypes -> {
            transactionTypes.migrate();
            // The DB2-derived transaction-type migrations already own V1/V2 in public.
            Flyway.configure()
                    .dataSource(transactionTypes.getConfiguration().getDataSource())
                    .schemas("vsam")
                    .locations("classpath:db/vsam")
                    .load()
                    .migrate();
        };
    }
}
