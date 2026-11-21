package com.carddemo.batch.posting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PostingRestartTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @TempDir
    Path temp;

    @Test
    void rollsBackFailedPostingAndResumesAtFailedReportWithoutPostingTwice() throws Exception {
        Path fixture = Path.of("../../fixtures/posting/p01-baseline-mixed");
        Path input = Files.createDirectory(temp.resolve("input"));
        try (var files = Files.list(fixture.resolve("input"))) {
            for (var file : files.toList()) Files.copy(file, input.resolve(file.getFileName()));
        }
        Path out = temp.resolve("out");
        Path postingBlocker = Files.createDirectories(out.resolve("sysout/POSTTRAN.STEP15.txt"));
        Path reportBlocker = Files.createDirectories(out.resolve("sysout/TRANREPT.STEP10R.txt"));
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource.setSchema("posting_restart");
        var jdbc = new JdbcTemplate(dataSource);

        assertThatThrownBy(() -> run(input, out)).hasMessage("posting failed: FAILED");
        assertThat(steps(jdbc)).containsExactly("POSTTRAN.STEP15:FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"transaction\"", Integer.class)).isZero();

        Files.delete(postingBlocker);
        assertThatThrownBy(() -> run(input, out)).hasMessage("posting failed: FAILED");
        assertThat(steps(jdbc)).containsExactly("POSTTRAN.STEP15:FAILED", "POSTTRAN.STEP15:COMPLETED",
                "TRANREPT.PRC001:COMPLETED", "TRANREPT.STEP05R:COMPLETED", "TRANREPT.STEP10R:FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"transaction\"", Integer.class)).isPositive();
        assertThat(out.resolve("rc.json")).doesNotExist();

        Path date = input.resolve("AWS.M2.CARDDEMO.DATEPARM.txt");
        byte[] original = Files.readAllBytes(date);
        Files.writeString(date, "changed");
        assertThatThrownBy(() -> run(input, out)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input contents changed");
        Files.write(date, original);

        Files.delete(reportBlocker);
        run(input, out);
        var completed = steps(jdbc);
        assertThat(completed).containsExactly("POSTTRAN.STEP15:FAILED", "POSTTRAN.STEP15:COMPLETED",
                "TRANREPT.PRC001:COMPLETED", "TRANREPT.STEP05R:COMPLETED", "TRANREPT.STEP10R:FAILED",
                "TRANREPT.STEP10R:COMPLETED");
        assertThat(jdbc.queryForList("SELECT status FROM batch_job_execution ORDER BY job_execution_id", String.class))
                .containsExactly("FAILED", "FAILED", "COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_job_instance", Integer.class)).isEqualTo(1);
        run(input, out);
        assertThat(steps(jdbc)).isEqualTo(completed);
        var expected = fixture.resolve("expected");
        try (var files = Files.walk(expected)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                var relative = expected.relativize(file);
                if (!relative.toString().equals("manifest.json")) {
                    assertThat(out.resolve(relative)).hasSameBinaryContentAs(file);
                }
            }
        }
    }

    private static List<String> steps(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT step_name || ':' || status FROM batch_step_execution ORDER BY step_execution_id",
                String.class);
    }

    private static void run(Path input, Path out) throws Exception {
        PostingCandidate.run(input, out, "2022-07-05T10:00:00Z", "restart", POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
