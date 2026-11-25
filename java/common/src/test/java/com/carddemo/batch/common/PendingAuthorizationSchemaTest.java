package com.carddemo.batch.common;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** G2: PAUTSUM0/PAUTDTL1 (CIPAUSMY/CIPAUDTY) and AUTHFRDS on PostgreSQL 15. */
@Testcontainers
class PendingAuthorizationSchemaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    private static final Charset EBCDIC = Charset.forName("Cp037");
    private static final Path UNLOAD = findRoot().resolve(
            "app/app-authorization-ims-db2-mq/data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat");

    private static final String SUMMARY = "account_id:S9(11):COMP-3 customer_id:9(09) auth_status:X(01) "
            + "account_status_1:X(02) account_status_2:X(02) account_status_3:X(02) account_status_4:X(02) "
            + "account_status_5:X(02) credit_limit:S9(09)V99:COMP-3 cash_limit:S9(09)V99:COMP-3 "
            + "credit_balance:S9(09)V99:COMP-3 cash_balance:S9(09)V99:COMP-3 approved_auth_count:S9(04):COMP "
            + "declined_auth_count:S9(04):COMP approved_auth_amount:S9(09)V99:COMP-3 "
            + "declined_auth_amount:S9(09)V99:COMP-3 filler:X(34)";
    private static final String DETAIL = "auth_date_inv:S9(05):COMP-3 auth_time_inv:S9(09):COMP-3 "
            + "auth_original_date:X(06) auth_original_time:X(06) card_number:X(16) auth_type:X(04) "
            + "card_expiry_date:X(04) message_type:X(06) message_source:X(06) auth_id_code:X(06) "
            + "auth_response_code:X(02) auth_response_reason:X(04) processing_code:9(06) "
            + "transaction_amount:S9(10)V99:COMP-3 approved_amount:S9(10)V99:COMP-3 merchant_category_code:X(04) "
            + "acquirer_country_code:X(03) pos_entry_mode:9(02) merchant_id:X(15) merchant_name:X(22) "
            + "merchant_city:X(13) merchant_state:X(02) merchant_zip:X(09) transaction_id:X(15) "
            + "match_status:X(01) fraud_status:X(01) fraud_report_date:X(08) filler:X(17)";
    private static final List<String> AUTH_FRAUD = List.of(
            "card_number character(16) not null", "auth_timestamp timestamp(6) without time zone not null",
            "auth_type character(4)", "card_expiry_date character(4)", "message_type character(6)",
            "message_source character(6)", "auth_id_code character(6)", "auth_response_code character(2)",
            "auth_response_reason character(4)", "processing_code character(6)",
            "transaction_amount numeric(12,2)", "approved_amount numeric(12,2)",
            "merchant_category_code character(4)", "acquirer_country_code character(3)",
            "pos_entry_mode smallint", "merchant_id character(15)", "merchant_name character varying(22)",
            "merchant_city character(13)", "merchant_state character(2)", "merchant_zip character(9)",
            "transaction_id character(15)", "match_status character(1)", "fraud_status character(1)",
            "fraud_report_date date", "account_id numeric(11,0)", "customer_id numeric(9,0)");

    private Connection connection;

    @BeforeAll
    static void migrateTwice() {
        var flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
    }

    @AfterEach
    void disconnect() throws SQLException {
        connection.close();
    }

    @BeforeEach
    void connect() throws SQLException {
        connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        execute("TRUNCATE pending_auth_detail, pending_auth_summary, auth_fraud");
    }

    @Test
    void segmentColumnsCoverEveryCopybookByteWithPicTypes() throws Exception {
        assertThat(segmentBytes(SUMMARY)).isEqualTo(100);
        assertThat(segmentBytes(DETAIL)).isEqualTo(200);
        assertThat(columns("pending_auth_summary")).containsExactlyElementsOf(sqlColumns(SUMMARY));
        assertThat(columns("pending_auth_detail"))
                .containsExactlyElementsOf(sqlColumns("account_id:S9(11):COMP-3 " + DETAIL));
        assertThat(columns("auth_fraud")).containsExactlyElementsOf(AUTH_FRAUD);
    }

    @Test
    void preservesMaxComp3NegativeAndHalfwordBoundaries() throws Exception {
        verifyBoundaries("pending_auth_summary", SUMMARY);
        insertSummary(1, 0, 0);
        execute("INSERT INTO pending_auth_detail VALUES (1, " + zeroValues(DETAIL) + ")");
        verifyBoundaries("pending_auth_detail", DETAIL);
    }

    @Test
    void detailNeedsItsSummaryAndAUniqueInvertedKey() throws Exception {
        rejects("INSERT INTO pending_auth_detail VALUES (7, " + zeroValues(DETAIL) + ")", "23503");
        insertSummary(7, 0, 0);
        execute("INSERT INTO pending_auth_detail VALUES (7, " + zeroValues(DETAIL) + ")");
        rejects("INSERT INTO pending_auth_detail SELECT * FROM pending_auth_detail", "23505");
        rejects("INSERT INTO pending_auth_summary SELECT * FROM pending_auth_summary", "23505");
        rejects("DELETE FROM pending_auth_summary", "23503");
    }

    @Test
    void unloadedChildrenReadBackNewestFirstInGnpOrder() throws Exception {
        byte[] unload = Files.readAllBytes(UNLOAD);
        List<String> gnpOrder = new ArrayList<>();
        int roots = 0;
        Long account = null;
        List<String> blankKeyRoots = new ArrayList<>();
        List<Long> lowValueStatusRoots = new ArrayList<>();
        int offset = 0;
        while (offset < unload.length) {
            int length = ((unload[offset] & 0xff) << 8) | (unload[offset + 1] & 0xff);
            String segment = new String(unload, offset + 10, 8, EBCDIC);
            if (length == 140) {
                assertThat(segment).isEqualTo("PAUTSUM0");
                roots++;
                if (!isPacked(unload, offset + 39, 6)) {
                    blankKeyRoots.add(HexFormat.of().formatHex(unload, offset + 39, offset + 45));
                    account = null;
                    offset += length;
                    continue;
                }
                account = packed(unload, offset + 39, 6);
                int approved = halfword(unload, offset + 39 + 50);
                int declined = halfword(unload, offset + 39 + 52);
                if (unload[offset + 39 + 15] == 0) lowValueStatusRoots.add(account);
                insertSummary(account, approved, declined);
            } else if (length == 240) {
                assertThat(segment).isEqualTo("PAUTDTL1");
                assertThat(account).as("child under a root whose key is not packed").isNotNull();
                int payload = offset + 39;
                long dateInv = packed(unload, payload, 3);
                long timeInv = packed(unload, payload + 3, 5);
                String original = new String(unload, payload + 8, 12, EBCDIC);
                try (var insert = connection.prepareStatement("INSERT INTO pending_auth_detail VALUES (?, ?, ?, ?, ?, "
                        + zeroValues(DETAIL).split(", ", 5)[4] + ")")) {
                    insert.setLong(1, account);
                    insert.setLong(2, dateInv);
                    insert.setLong(3, timeInv);
                    insert.setString(4, original.substring(0, 6));
                    insert.setString(5, original.substring(6));
                    insert.executeUpdate();
                }
                gnpOrder.add(account + " " + original);
            }
            offset += length;
        }
        assertThat(offset).isEqualTo(unload.length);
        assertThat(roots).isEqualTo(22);
        assertThat(blankKeyRoots).as("childless root keyed by spaces: no NUMERIC account_id; G3 rejects it")
                .containsExactly("404040404040");
        assertThat(gnpOrder).hasSize(202);
        assertThat(lowValueStatusRoots).as("X'00' statuses need codec handling: PostgreSQL text has no NUL")
                .containsExactly(7L, 13L, 18L);
        try (var update = connection.prepareStatement("UPDATE pending_auth_summary SET auth_status = ?")) {
            update.setString(1, "\u0000");
            assertThat(assertThrows(SQLException.class, update::executeUpdate).getSQLState()).isEqualTo("22021");
        }

        List<String> sqlOrder = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT account_id, auth_original_date || auth_original_time FROM pending_auth_detail
                ORDER BY account_id, auth_date_inv, auth_time_inv""")) {
            while (rows.next()) sqlOrder.add(rows.getLong(1) + " " + rows.getString(2));
        }
        assertThat(sqlOrder).containsExactlyElementsOf(gnpOrder);
        for (int i = 1; i < sqlOrder.size(); i++) {
            String previous = sqlOrder.get(i - 1);
            String current = sqlOrder.get(i);
            if (previous.split(" ")[0].equals(current.split(" ")[0])) {
                assertThat(current.split(" ")[1]).as("newest first").isLessThanOrEqualTo(previous.split(" ")[1]);
            }
        }
    }

    @Test
    void invertedKeyFromCopauaFormulaSortsNewestFirst() throws Exception {
        insertSummary(9, 0, 0);
        String[][] stamps = {{"24001", "000000000"}, {"23365", "235959999"}, {"24001", "120000500"},
                {"24001", "120000499"}};
        for (String[] stamp : stamps) {
            long dateInv = 99999 - Long.parseLong(stamp[0]);
            long timeInv = 999999999 - Long.parseLong(stamp[1]);
            execute("INSERT INTO pending_auth_detail VALUES (9, " + dateInv + ", " + timeInv + ", '"
                    + stamp[0] + "', '" + stamp[1].substring(0, 6) + "', "
                    + zeroValues(DETAIL).split(", ", 5)[4] + ")");
        }
        List<String> order = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT 99999 - auth_date_inv, 999999999 - auth_time_inv FROM pending_auth_detail
                WHERE account_id = 9 ORDER BY auth_date_inv, auth_time_inv""")) {
            while (rows.next()) order.add(rows.getLong(1) + " " + rows.getLong(2));
        }
        assertThat(order).containsExactly("24001 120000500", "24001 120000499", "24001 0", "23365 235959999");
    }

    @Test
    void authFraudKeepsTheDb2KeyNullabilityAndDescendingIndex() throws Exception {
        try (var insert = connection.prepareStatement(
                "INSERT INTO auth_fraud (card_number, auth_timestamp, transaction_amount, account_id) "
                        + "VALUES ('4000000000000002', ?, ?, ?)")) {
            insert.setTimestamp(1, Timestamp.valueOf("2023-10-27 04:12:52.555123"));
            insert.setBigDecimal(2, new BigDecimal("-9999999999.99"));
            insert.setBigDecimal(3, new BigDecimal("99999999999"));
            insert.executeUpdate();
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT auth_timestamp, transaction_amount, merchant_name FROM auth_fraud")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getTimestamp(1)).isEqualTo(Timestamp.valueOf("2023-10-27 04:12:52.555123"));
            assertThat(rows.getBigDecimal(2)).isEqualTo(new BigDecimal("-9999999999.99"));
            assertThat(rows.getString(3)).isNull();
        }
        rejects("INSERT INTO auth_fraud SELECT * FROM auth_fraud", "23505");
        rejects("INSERT INTO auth_fraud (card_number) VALUES ('4000000000000002')", "23502");
        rejects("UPDATE auth_fraud SET transaction_amount = 10000000000", "22003");
        rejects("UPDATE auth_fraud SET merchant_name = '" + "Z".repeat(23) + "'", "22001");
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT pg_get_indexdef('auth_fraud_card_ts_desc'::regclass)")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).endsWith("USING btree (card_number, auth_timestamp DESC)")
                    .contains("UNIQUE");
        }
    }

    private void verifyBoundaries(String table, String layout) throws Exception {
        if (table.equals("pending_auth_summary")) {
            execute("INSERT INTO " + table + " VALUES (" + zeroValues(layout) + ")");
        }
        for (String field : layout.split(" ")) {
            String[] parts = field.split(":");
            String column = parts[0];
            String pic = parts[1];
            String update = "UPDATE " + table + " SET " + column + " = ";
            rejects(update + "NULL", "23502");
            if (pic.startsWith("X")) {
                int width = digits(pic);
                execute(update + "'Z'");
                assertThat(single(table, column)).isEqualTo("Z" + " ".repeat(width - 1));
                rejects(update + "'" + "Z".repeat(width + 1) + "'", "22001");
            } else if (field.endsWith(":COMP")) {
                for (String value : List.of("32767", "-32768", "-9999", "0")) {
                    execute(update + value);
                    assertThat(single(table, column)).isEqualTo(value);
                }
                rejects(update + "32768", "22003");
                rejects(update + "-32769", "22003");
            } else {
                int scale = pic.contains("V") ? 2 : 0;
                int precision = digits(pic) + scale;
                BigDecimal max = BigDecimal.TEN.pow(precision).subtract(BigDecimal.ONE).movePointLeft(scale);
                execute(update + max.toPlainString());
                assertThat(single(table, column)).isEqualTo(max.toPlainString());
                if (pic.startsWith("S")) {
                    execute(update + max.negate().toPlainString());
                    assertThat(single(table, column)).isEqualTo(max.negate().toPlainString());
                    rejects(update + BigDecimal.TEN.pow(precision - scale).negate().toPlainString(), "22003");
                } else {
                    rejects(update + "-1", "23514");
                }
                rejects(update + BigDecimal.TEN.pow(precision - scale).toPlainString(), "22003");
                execute(update + "0");
            }
        }
        execute("DELETE FROM " + table);
    }

    private static int segmentBytes(String layout) {
        return Arrays.stream(layout.split(" ")).mapToInt(field -> {
            String pic = field.split(":")[1];
            int precision = digits(pic) + (pic.contains("V") ? 2 : 0);
            if (field.endsWith(":COMP-3")) return precision / 2 + 1;
            if (field.endsWith(":COMP")) return precision <= 4 ? 2 : precision <= 9 ? 4 : 8;
            return precision;
        }).sum();
    }

    private static List<String> sqlColumns(String layout) {
        return Arrays.stream(layout.split(" ")).map(field -> {
            String[] parts = field.split(":");
            String pic = parts[1];
            String type;
            if (pic.startsWith("X")) type = "character(" + digits(pic) + ")";
            else if (field.endsWith(":COMP")) type = "smallint";
            else {
                int scale = pic.contains("V") ? 2 : 0;
                type = "numeric(" + (digits(pic) + scale) + "," + scale + ")";
            }
            return parts[0] + " " + type + " not null";
        }).collect(Collectors.toList());
    }

    private List<String> columns(String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                SELECT attname || ' ' || format_type(atttypid, atttypmod) || CASE WHEN attnotnull THEN ' not null' ELSE '' END
                FROM pg_attribute WHERE attrelid = ?::regclass AND attnum > 0 AND NOT attisdropped ORDER BY attnum""")) {
            statement.setString(1, table);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) columns.add(rows.getString(1));
            }
        }
        return columns;
    }

    private static String zeroValues(String layout) {
        return Arrays.stream(layout.split(" ")).map(field -> field.contains(":X") ? "''" : "0")
                .collect(Collectors.joining(", "));
    }

    private void insertSummary(long account, int approved, int declined) throws SQLException {
        String[] values = zeroValues(SUMMARY).split(", ");
        values[0] = Long.toString(account);
        values[12] = Integer.toString(approved);
        values[13] = Integer.toString(declined);
        execute("INSERT INTO pending_auth_summary VALUES (" + String.join(", ", values) + ")");
    }

    private static int digits(String pic) {
        return Integer.parseInt(pic.substring(pic.indexOf('(') + 1, pic.indexOf(')')));
    }

    private static long packed(byte[] bytes, int offset, int length) {
        String hex = HexFormat.of().formatHex(bytes, offset, offset + length);
        long value = Long.parseLong(hex.substring(0, hex.length() - 1));
        char sign = hex.charAt(hex.length() - 1);
        assertThat(sign).isIn('c', 'd', 'f');
        return sign == 'd' ? -value : value;
    }

    private static boolean isPacked(byte[] bytes, int offset, int length) {
        String hex = HexFormat.of().formatHex(bytes, offset, offset + length);
        return hex.substring(0, hex.length() - 1).chars().allMatch(Character::isDigit)
                && "cdf".indexOf(hex.charAt(hex.length() - 1)) >= 0;
    }

    private static int halfword(byte[] bytes, int offset) {
        return (short) (((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff));
    }

    private String single(String table, String column) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT " + column + " FROM " + table)) {
            assertThat(rows.next()).isTrue();
            String value = rows.getString(1);
            assertThat(rows.next()).isFalse();
            return value;
        }
    }

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void rejects(String sql, String sqlState) {
        SQLException failure = assertThrows(SQLException.class, () -> execute(sql));
        assertThat(failure.getSQLState()).isEqualTo(sqlState);
    }

    private static Path findRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("fixtures"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("Run tests inside the estate repository");
        return path;
    }
}
