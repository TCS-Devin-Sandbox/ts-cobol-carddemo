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
            "account acct_id:9(11) acct_active_status:X(01) acct_curr_bal:S9(10)V99 acct_credit_limit:S9(10)V99 acct_cash_credit_limit:S9(10)V99 acct_open_date:X(10) acct_expiraion_date:X(10) acct_reissue_date:X(10) acct_curr_cyc_credit:S9(10)V99 acct_curr_cyc_debit:S9(10)V99 acct_addr_zip:X(10) acct_group_id:X(10) filler:X(178)",
            "card card_num:X(16) card_acct_id:9(11) card_cvv_cd:9(03) card_embossed_name:X(50) card_expiraion_date:X(10) card_active_status:X(01) filler:X(59)",
            "customer cust_id:9(09) cust_first_name:X(25) cust_middle_name:X(25) cust_last_name:X(25) cust_addr_line_1:X(50) cust_addr_line_2:X(50) cust_addr_line_3:X(50) cust_addr_state_cd:X(02) cust_addr_country_cd:X(03) cust_addr_zip:X(10) cust_phone_num_1:X(15) cust_phone_num_2:X(15) cust_ssn:9(09) cust_govt_issued_id:X(20) cust_dob_yyyy_mm_dd:X(10) cust_eft_account_id:X(10) cust_pri_card_holder_ind:X(01) cust_fico_credit_score:9(03) filler:X(168)",
            "card_xref xref_card_num:X(16) xref_cust_id:9(09) xref_acct_id:9(11) filler:X(14)",
            "tran_cat_bal trancat_acct_id:9(11) trancat_type_cd:X(02) trancat_cd:9(04) tran_cat_bal:S9(09)V99 filler:X(22)",
            "disclosure_group dis_acct_group_id:X(10) dis_tran_type_cd:X(02) dis_tran_cat_cd:9(04) dis_int_rate:S9(04)V99 filler:X(28)",
            "transaction tran_id:X(16) tran_type_cd:X(02) tran_cat_cd:9(04) tran_source:X(10) tran_desc:X(100) tran_amt:S9(09)V99 tran_merchant_id:9(09) tran_merchant_name:X(50) tran_merchant_city:X(50) tran_merchant_zip:X(10) tran_card_num:X(16) tran_orig_ts:X(26) tran_proc_ts:X(26) filler:X(20)",
            "tran_type tran_type:X(02) tran_type_desc:X(50) filler:X(08)",
            "tran_category tran_type_cd:X(02) tran_cat_cd:9(04) tran_cat_type_desc:X(50) filler:X(04)"
    };

    @Test
    void migratesAndPreservesEveryPicBoundary() throws Exception {
        var flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
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
