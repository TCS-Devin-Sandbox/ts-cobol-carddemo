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
            var config = transactionTypes.getConfiguration();
            // Reuse the existing schema without competing for the DB2-derived V1/V2 history.
            Flyway.configure()
                    .dataSource(config.getDataSource())
                    .schemas(config.getSchemas())
                    .defaultSchema(config.getDefaultSchema())
                    .createSchemas(false)
                    .table("flyway_vsam_schema_history")
                    .locations("classpath:db/vsam")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate();
        };
    }
}
