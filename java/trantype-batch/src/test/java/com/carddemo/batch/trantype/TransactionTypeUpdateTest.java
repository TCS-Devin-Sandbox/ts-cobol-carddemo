package com.carddemo.batch.trantype;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class TransactionTypeUpdateTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate db;

    @TempDir
    Path directory;

    @BeforeAll
    static void createTables() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new ResourceDatabasePopulator(new FileSystemResource("../../db/postgres/01-transaction-type.sql"))
                .execute(dataSource);
        db = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void seed() {
        db.execute("TRUNCATE carddemo.transaction_type CASCADE");
        db.update("INSERT INTO carddemo.transaction_type VALUES ('01', 'Purchase')");
    }

    @Test
    void duplicateAbortsAllWritesButTheJobProcessesTheRemainingRecords() throws Exception {
        Path input = Files.writeString(directory.resolve("input"), "A07Before error\nA01Duplicate\nX08Invalid\nA08After error\n");
        Path sysout = directory.resolve("sysout");

        assertThat(new TransactionTypeUpdate(dataSource).run(input, sysout)).isEqualTo(4);
        assertThat(db.queryForList("SELECT tr_type FROM carddemo.transaction_type", String.class)).containsExactly("01");
        assertThat(Files.readAllLines(sysout)).containsSubsequence(
                "RECORD INSERTED SUCCESSFULLY",
                "Error accessing: TRANSACTION_TYPE table. SQLCODE: -403" + " ".repeat(26),
                "ERROR: TYPE NOT VALIDSACTION_TYPE table. SQLCODE: -403" + " ".repeat(26),
                "Error accessing: TRANSACTION_TYPE table. SQLCODE: -400" + " ".repeat(26),
                "DSNRUN: COMMIT AT END OF TASK");
    }

    @Test
    void missingUpdateSucceedsAndInvalidOperationDoesNotAbortTheTransaction() throws Exception {
        Path input = Files.writeString(directory.resolve("input"), "U09Absent\nX08Invalid\nU01Revised\nA02"
                + "X".repeat(51) + "\n");
        Path sysout = directory.resolve("sysout");

        assertThat(new TransactionTypeUpdate(dataSource).run(input, sysout)).isEqualTo(4);
        assertThat(db.queryForList("SELECT tr_description FROM carddemo.transaction_type ORDER BY tr_type", String.class))
                .containsExactly("Revised" + " ".repeat(43), "X".repeat(50));
        assertThat(Files.readAllLines(sysout)).containsSubsequence(
                "RECORD UPDATED SUCCESSFULLY", "ERROR: TYPE NOT VALID" + " ".repeat(59),
                "RECORD UPDATED SUCCESSFULLY", "RECORD INSERTED SUCCESSFULLY");
    }
}
