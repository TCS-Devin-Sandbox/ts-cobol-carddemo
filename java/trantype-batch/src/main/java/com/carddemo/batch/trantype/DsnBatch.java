package com.carddemo.batch.trantype;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

final class DsnBatch {
    private DsnBatch() { }

    @FunctionalInterface
    interface Program {
        int run(Connection connection, Writer output) throws IOException, SQLException;
    }

    static int run(DataSource dataSource, Path sysout, String programName, Program program)
            throws IOException, SQLException {
        Files.createDirectories(sysout.getParent());
        var connection = DataSourceUtils.getConnection(dataSource);
        boolean joined = DataSourceUtils.isConnectionTransactional(connection, dataSource);
        try (var output = Files.newBufferedWriter(sysout, ISO_8859_1)) {
            if (!joined) connection.setAutoCommit(false);
            var savepoint = joined ? connection.setSavepoint() : null;
            String searchPath = joined ? searchPath(connection) : null;
            line(output, "DSNRUN: DSN ATTACHED, RUNNING " + programName);
            int rc = program.run(connection, output);
            if (joined) finishProgram(connection, savepoint, searchPath);
            else connection.commit();
            line(output, "DSNRUN: COMMIT AT END OF TASK");
            return rc;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static String searchPath(Connection connection) throws SQLException {
        try (var sql = connection.createStatement(); var row = sql.executeQuery("SHOW search_path")) {
            row.next();
            return row.getString(1);
        }
    }

    private static void finishProgram(Connection connection, Savepoint savepoint, String searchPath) throws SQLException {
        try (var sql = connection.prepareStatement("SELECT set_config('search_path', ?, true)")) {
            sql.setString(1, searchPath);
            sql.execute();
        } catch (SQLException e) {
            if (!"25P02".equals(e.getSQLState())) throw e;
            // DSNEXIT rolls back all writes after a SQL error. Keep the outer checkpoint transaction usable.
            connection.rollback(savepoint);
        }
        connection.releaseSavepoint(savepoint);
    }

    static int sqlCode(SQLException e) {
        return "23505".equals(e.getSQLState()) ? -403 : -400;
    }

    static void line(Writer output, String text) throws IOException {
        output.write(text);
        output.write('\n');
    }
}
