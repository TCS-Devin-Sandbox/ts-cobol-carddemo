package com.carddemo.batch.trantype;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;

import org.postgresql.PGConnection;
import org.postgresql.core.Parser;

import static com.carddemo.batch.trantype.DsnBatch.line;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

final class DynamicTransactionTypeUpdate {
    private static final int SQL_COLUMNS = 72;
    private static final int SQL_BUFFER_WIDTH = 800;
    private static final String SPACE_PADDING = " ".repeat(SQL_COLUMNS);

    @FunctionalInterface
    private interface FollowUp {
        int run(Path input, Writer output, Connection connection) throws IOException, SQLException;
    }

    private static final Map<String, FollowUp> FOLLOW_UPS = Map.of("COBTUPDT", TransactionTypeUpdate::apply);

    private final DataSource dataSource;

    DynamicTransactionTypeUpdate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    int run(Path dynsql, Path input, String parm, Path sysout) throws IOException, SQLException {
        String targetName = parm.stripTrailing();
        FollowUp target = FOLLOW_UPS.get(targetName);
        if (!targetName.isEmpty() && target == null) {
            throw new IllegalArgumentException("Unregistered MNTTRDYN follow-up program: " + targetName);
        }
        if (target != null && input == null) {
            throw new IllegalArgumentException("INPFILE is required for " + targetName);
        }
        SqlInput sql = readSql(dynsql);
        return DsnBatch.run(dataSource, sysout, "CBTRNDYN", (connection, output) -> {
            line(output, String.format(Locale.ROOT, "CBTRNDYN: %02d SQL LINES READ", sql.lines()));
            int sqlCode = execute(connection, sql.statement());
            line(output, String.format(Locale.ROOT, "CBTRNDYN: EXECUTE SQLCODE %+011d", sqlCode));

            if (target != null) {
                line(output, "CBTRNDYN: CALLING " + targetName);
                // COBTUPDT does STOP RUN, so the target's RC is the step RC and CBTRNDYN never prints END.
                return target.run(input, output, connection);
            }
            line(output, "CBTRNDYN: END");
            return sqlCode < 0 ? 8 : 0;
        });
    }

    private static SqlInput readSql(Path path) throws IOException {
        var sql = new StringBuilder(SQL_BUFFER_WIDTH);
        int lines = 0;
        try (var reader = Files.newBufferedReader(path, ISO_8859_1)) {
            for (String record; (record = reader.readLine()) != null; ) {
                if (record.startsWith("--")) continue;
                String text = (record + SPACE_PADDING).substring(0, SQL_COLUMNS) + " ";
                sql.append(text, 0, Math.min(text.length(), SQL_BUFFER_WIDTH - sql.length()));
                lines = (lines + 1) % 100;
            }
        }
        return new SqlInput(lines, sql.toString());
    }

    private record SqlInput(int lines, String statement) { }

    private static int execute(Connection connection, String sql) throws SQLException {
        if (sql.isBlank()) return -212;
        boolean standardStrings = "on".equals(
                connection.unwrap(PGConnection.class).getParameterStatus("standard_conforming_strings"));
        var queries = Parser.parseJdbcSql(sql, standardStrings, true, true, false, false);
        if (queries.size() != 1 || queries.getFirst().bindPositions.length != 0
                || switch (queries.getFirst().getCommand().getType()) {
                    case INSERT, UPDATE, DELETE -> false;
                    default -> true;
                }) {
            throw new IllegalArgumentException("DYNSQL must contain one INSERT, UPDATE or DELETE without parameters");
        }

        try (var setup = connection.createStatement()) {
            setup.execute("SET LOCAL search_path TO carddemo");
        }
        try (var statement = connection.prepareStatement(sql)) {
            statement.execute();
            return 0;
        } catch (SQLException failure) {
            return DsnBatch.sqlCode(failure);
        }
    }
}
