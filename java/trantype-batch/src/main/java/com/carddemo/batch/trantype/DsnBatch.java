package com.carddemo.batch.trantype;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

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
        try (var output = Files.newBufferedWriter(sysout, ISO_8859_1);
             var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            line(output, "DSNRUN: DSN ATTACHED, RUNNING " + programName);
            int rc = program.run(connection, output);
            // PostgreSQL COMMIT on an aborted transaction rolls back, as it does for DSNEXIT.
            connection.commit();
            line(output, "DSNRUN: COMMIT AT END OF TASK");
            return rc;
        }
    }

    static int sqlCode(SQLException e) {
        return "23505".equals(e.getSQLState()) ? -403 : -400;
    }

    static void line(Writer output, String text) throws IOException {
        output.write(text);
        output.write('\n');
    }
}
