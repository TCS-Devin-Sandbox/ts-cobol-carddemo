package com.carddemo.batch.trantype;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

final class TransactionTypeUpdate {
    private static final int RECORD_WIDTH = 53;
    private static final String RECORD_PADDING = " ".repeat(RECORD_WIDTH);
    private static final int MESSAGE_WIDTH = 80;
    private final DataSource dataSource;

    TransactionTypeUpdate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    int run(Path input, Path sysout) throws IOException, SQLException {
        Files.createDirectories(sysout.getParent());
        try (var records = Files.newBufferedReader(input, ISO_8859_1);
             var output = Files.newBufferedWriter(sysout, ISO_8859_1);
             var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            return process(records, output, connection);
        }
    }

    private static int process(BufferedReader records, Writer output, Connection connection)
            throws IOException, SQLException {
        line(output, "DSNRUN: DSN ATTACHED, RUNNING COBTUPDT");
        line(output, "OPEN FILE OK");
        var message = new StringBuilder(" ".repeat(MESSAGE_WIDTH));
        int rc = 0;
        for (String record; (record = records.readLine()) != null; ) {
            record = (record + RECORD_PADDING).substring(0, RECORD_WIDTH);
            line(output, "PROCESSING   " + record);
            char operation = record.charAt(0);
            if (operation == '*') {
                line(output, "IGNORING COMMENTED LINE");
                continue;
            }
            String error = switch (operation) {
                case 'A', 'U', 'D' -> apply(connection, output, operation, record.substring(1, 3), record.substring(3));
                default -> "ERROR: TYPE NOT VALID";
            };
            if (error != null) {
                // COBOL STRING overwrites the prefix, leaving the rest of the previous message intact.
                message.replace(0, error.length(), error);
                line(output, message.toString());
                rc = 4;
            }
        }
        // PostgreSQL COMMIT on an aborted transaction rolls back, as it does for DSNEXIT.
        connection.commit();
        line(output, "DSNRUN: COMMIT AT END OF TASK");
        return rc;
    }

    private static String apply(Connection connection, Writer output, char operation, String code, String description)
            throws IOException {
        String sql;
        String verb;
        String result;
        switch (operation) {
            case 'A' -> {
                sql = "INSERT INTO carddemo.transaction_type (tr_type, tr_description) VALUES (?, ?)";
                verb = "ADDING";
                result = "INSERTED";
            }
            case 'U' -> {
                sql = "UPDATE carddemo.transaction_type SET tr_description = ? WHERE tr_type = CAST(? AS CHAR(2))";
                verb = "UPDATING";
                result = "UPDATED";
            }
            case 'D' -> {
                sql = "DELETE FROM carddemo.transaction_type WHERE tr_type = CAST(? AS CHAR(2))";
                verb = "DELETING";
                result = "DELETED";
            }
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        }
        line(output, verb + " RECORD");
        try (var statement = connection.prepareStatement(sql)) {
            if (operation == 'U') {
                statement.setString(1, description);
                statement.setString(2, code);
            } else {
                statement.setString(1, code);
                if (operation == 'A') statement.setString(2, description);
            }
            // ocesql reports SQLCODE 0 for UPDATE/DELETE even when no row matches.
            statement.executeUpdate();
        } catch (SQLException e) {
            return "Error accessing: TRANSACTION_TYPE table. SQLCODE: "
                    + ("23505".equals(e.getSQLState()) ? "-403" : "-400");
        }
        line(output, "RECORD " + result + " SUCCESSFULLY");
        return null;
    }

    private static void line(Writer output, String text) throws IOException {
        output.write(text);
        output.write('\n');
    }
}
