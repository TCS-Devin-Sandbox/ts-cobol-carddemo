package com.carddemo.interest.pilot;

import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real PostgreSQL 15 (S16: no H2). One container for the whole JVM so the cached Spring context keeps a
 * live connection across test classes; Ryuk removes it at JVM exit.
 */
@SpringBootTest
abstract class PilotPostgresTestSupport {

    static final Path REPO = Path.of("../../..").toAbsolutePath().normalize();
    static final Path I01 = REPO.resolve("fixtures/interest/i01-baseline");
    static final Path SPECIMENS = Path.of("src/test/resources/pilot/specimens").toAbsolutePath();

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    PilotRunService service;

    @Autowired
    JdbcTemplate jdbc;

    int count(String table, UUID runId) {
        return jdbc.queryForObject("SELECT count(*) FROM interest_pilot." + table + " WHERE run_id = ?",
                Integer.class, runId);
    }

    int committedOutputs(UUID runId) {
        return count("interest_tx", runId) + count("account_delta", runId) + count("sysout_line", runId)
                + count("run_checkpoint", runId);
    }
}
