package com.carddemo.batch.posting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import com.carddemo.batch.common.VsamDataset;
import com.carddemo.batch.common.VsamTransfer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.carddemo.batch.posting.PostingTasklet.PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ReportOnlyTest {
    private static final Path FIXTURES = Path.of("../../fixtures/posting");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(dataSource()).load().migrate();
    }

    @ParameterizedTest
    @MethodSource("postingCases")
    void launchedTranreptMatchesLegacyAndDoesNotRepost(Path fixture, @TempDir Path out) throws Exception {
        stagePostedRows(fixture);
        run(out, "2022-01-01", "2022-07-06");
        var expected = fixture.resolve("expected");

        assertThat(TransactionReportJob.dataset(out, "TRANSACT.BKUP"))
                .hasSameBinaryContentAs(expected.resolve("datasets/" + PREFIX + "TRANSACT.VSAM.KSDS.txt"));
        var report = TransactionReportJob.dataset(out, "TRANREPT");
        assertThat(report).hasSameBinaryContentAs(expected.resolve("datasets").resolve(report.getFileName()));
        var sysout = Path.of("sysout/TRANREPT.STEP10R.txt");
        assertThat(out.resolve(sysout)).hasSameBinaryContentAs(expected.resolve(sysout));
        var result = new com.fasterxml.jackson.databind.ObjectMapper().readTree(out.resolve("rc.json").toFile());
        var golden = new com.fasterxml.jackson.databind.ObjectMapper().readTree(expected.resolve("rc.json").toFile());
        assertThat(result.get("TRANREPT")).isEqualTo(golden.get("TRANREPT"));
        assertThat(result.has("POSTTRAN")).isFalse();
        assertThat(out.resolve("sysout/POSTTRAN.STEP15.txt")).doesNotExist();
    }

    @Test
    void customDateReachesBothSortAndReportOutsideTheFixtureWindow(@TempDir Path out) throws Exception {
        stagePostedRows(FIXTURES.resolve("p01-baseline-mixed"));
        var jdbc = new JdbcTemplate(dataSource());
        var moved = jdbc.queryForObject("SELECT min(transaction_id) FROM \"transaction\"", String.class);
        jdbc.update("""
                UPDATE "transaction" SET processed_timestamp = '2025-10-01' || substring(processed_timestamp, 11)
                WHERE transaction_id = ?
                """, moved);

        run(out, "2025-10-01", "2025-10-01");
        assertThat(Files.readAllLines(TransactionReportJob.dataset(out, "TRANSACT.DALY")))
                .singleElement().asString().startsWith(moved);
        assertThat(out.resolve("sysout/TRANREPT.STEP10R.txt"))
                .content().contains("Reporting from 2025-10-01 to 2025-10-01");
        assertThat(TransactionReportJob.dataset(out, "TRANREPT")).content().contains(moved);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"transaction\"", Integer.class)).isGreaterThan(1);
    }

    static List<Path> postingCases() throws Exception {
        try (var paths = Files.list(FIXTURES)) {
            return paths.filter(Files::isDirectory).sorted().toList();
        }
    }

    private static void stagePostedRows(Path fixture) throws Exception {
        try (var connection = dataSource().getConnection()) {
            connection.setAutoCommit(false);
            var transfer = new VsamTransfer(connection);
            transfer.load(VsamDataset.TRANSACTION,
                    fixture.resolve("expected/datasets/" + PREFIX + "TRANSACT.VSAM.KSDS.txt"));
            for (var kind : List.of(VsamDataset.CARD_XREF, VsamDataset.TYPE, VsamDataset.CATEGORY)) {
                transfer.load(kind, fixture.resolve("input/" + PREFIX + kind.inputName + ".PS.txt"));
            }
            connection.commit();
        }
    }

    private static void run(Path out, String start, String end) throws Exception {
        PostingCandidate.runReport(out, LocalDate.parse(start), LocalDate.parse(end),
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
