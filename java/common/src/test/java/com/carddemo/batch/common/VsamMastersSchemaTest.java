package com.carddemo.batch.common;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class VsamMastersSchemaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    private static final String[] LAYOUTS = {
            "account account_id:9(11) active_status:X(01) current_balance:S9(10)V99 credit_limit:S9(10)V99 cash_credit_limit:S9(10)V99 open_date:X(10) expiration_date:X(10) reissue_date:X(10) current_cycle_credit:S9(10)V99 current_cycle_debit:S9(10)V99 zip_code:X(10) account_group_id:X(10) filler:X(178)",
            "card card_number:X(16) account_id:9(11) card_verification_value:9(03) embossed_name:X(50) expiration_date:X(10) active_status:X(01) filler:X(59)",
            "customer customer_id:9(09) first_name:X(25) middle_name:X(25) last_name:X(25) address_line_1:X(50) address_line_2:X(50) address_line_3:X(50) state_code:X(02) country_code:X(03) zip_code:X(10) phone_number_1:X(15) phone_number_2:X(15) social_security_number:9(09) government_issued_id:X(20) date_of_birth:X(10) electronic_funds_transfer_account_id:X(10) primary_cardholder_indicator:X(01) fico_credit_score:9(03) filler:X(168)",
            "card_xref card_number:X(16) customer_id:9(09) account_id:9(11) filler:X(14)",
            "transaction_category_balance account_id:9(11) transaction_type_code:X(02) transaction_category_code:9(04) balance:S9(09)V99 filler:X(22)",
            "disclosure_group account_group_id:X(10) transaction_type_code:X(02) transaction_category_code:9(04) interest_rate:S9(04)V99 filler:X(28)",
            "transaction transaction_id:X(16) transaction_type_code:X(02) transaction_category_code:9(04) source:X(10) description:X(100) amount:S9(09)V99 merchant_id:9(09) merchant_name:X(50) merchant_city:X(50) merchant_zip_code:X(10) card_number:X(16) origination_timestamp:X(26) processed_timestamp:X(26) filler:X(20)",
            "tran_type transaction_type_code:X(02) description:X(50) filler:X(08)",
            "tran_category transaction_type_code:X(02) transaction_category_code:9(04) description:X(50) filler:X(04)"
    };

    @Test
    void migratesAndPreservesEveryPicBoundary() throws Exception {
        var flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("1")
                .load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (String layout : LAYOUTS) {
                verifyLayout(connection, layout);
            }
        }
    }

    private void verifyLayout(Connection connection, String layout) throws Exception {
        String[] parts = layout.split(" ");
        String table = "\"" + parts[0] + "\"";
        String[] fields = Arrays.copyOfRange(parts, 1, parts.length);
        try (var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT * FROM " + table)) {
                assertThat(rows.next()).isFalse();
                assertThat(rows.getMetaData().getColumnCount()).isEqualTo(fields.length);
            }
            String values = Arrays.stream(fields)
                    .map(field -> field.contains(":X") ? "''" : "0")
                    .collect(Collectors.joining(", "));
            statement.executeUpdate("INSERT INTO " + table + " VALUES (" + values + ")");
            rejects(connection, "INSERT INTO " + table + " SELECT * FROM " + table, "23505");
            for (String field : fields) {
                String column = field.split(":")[0];
                String pic = field.split(":")[1];
                int width = Integer.parseInt(pic.substring(pic.indexOf('(') + 1, pic.indexOf(')')));
                String update = "UPDATE " + table + " SET " + column + " = ";
                rejects(connection, update + "NULL", "23502");
                if (pic.startsWith("X")) {
                    statement.executeUpdate(update + "'Z'");
                    try (var rows = statement.executeQuery("SELECT " + column + " FROM " + table)) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo("Z" + " ".repeat(width - 1));
                    }
                    rejects(connection, update + "'" + "Z".repeat(width + 1) + "'", "22001");
                } else {
                    int scale = pic.contains("V") ? 2 : 0;
                    BigDecimal max = BigDecimal.TEN.pow(width).subtract(
                            BigDecimal.ONE.movePointLeft(scale)).setScale(scale);
                    assertNumeric(connection, table, column, BigDecimal.ZERO.setScale(scale));
                    assertNumeric(connection, table, column, max);
                    if (pic.startsWith("S")) {
                        assertNumeric(connection, table, column, max.negate());
                    } else {
                        rejects(connection, update + "-1", "23514");
                    }
                    rejects(connection, update + BigDecimal.TEN.pow(width).toPlainString(), "22003");
                    if (pic.startsWith("S")) {
                        rejects(connection, update + BigDecimal.TEN.pow(width).negate().toPlainString(),
                                "22003");
                    }
                }
            }
            statement.executeUpdate("DELETE FROM " + table);
        }
    }

    private void assertNumeric(Connection connection, String table, String column, BigDecimal value)
            throws Exception {
        try (var update = connection.prepareStatement("UPDATE " + table + " SET " + column + " = ?")) {
            update.setBigDecimal(1, value);
            assertThat(update.executeUpdate()).isEqualTo(1);
        }
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT " + column + " FROM " + table)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBigDecimal(1)).isEqualTo(value);
        }
    }

    private void rejects(Connection connection, String sql, String sqlState) {
        SQLException failure = assertThrows(SQLException.class, () -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        });
        assertThat(failure.getSQLState()).isEqualTo(sqlState);
    }
}
