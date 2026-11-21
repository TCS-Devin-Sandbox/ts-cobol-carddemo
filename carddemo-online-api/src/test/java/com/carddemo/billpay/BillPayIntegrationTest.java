package com.carddemo.billpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;

/** One test per COBIL00C paragraph (or EVALUATE branch) that the Enter key reaches. */
@SpringBootTest(properties = "carddemo.db.lock-timeout=300ms")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
class BillPayIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void seed() {
        jdbc.update("DELETE FROM \"transaction\"");
        jdbc.update("DELETE FROM card_xref WHERE account_id IN (42, 43, 44, 45)");
        jdbc.update("DELETE FROM account WHERE account_id IN (42, 43, 44, 45)");
        account(42, "250.00");
        account(43, "0.00");
        account(44, "-10.00");
        account(45, "75.25");
        jdbc.update("INSERT INTO card_xref VALUES ('4000000000000422', 7, 42, ''), ('4000000000000421', 7, 42, ''),"
                + " ('4000000000000430', 7, 43, ''), ('4000000000000440', 7, 44, ''), ('4000000000000450', 7, 45, '')");
        transaction("0000000000000041", "4000000000000430");
    }

    @AfterEach void dropTriggers() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_insert ON \"transaction\"");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_update ON account");
    }

    private void account(long id, String balance) {
        jdbc.update("INSERT INTO account VALUES (?, 'Y', ?, 1000.00, 200.00, '2020-01-01', '2030-12-31', "
                + "'2025-03-01', 300.00, -50.00, '00000', 'GROUP', '')", id, new BigDecimal(balance));
    }

    private void transaction(String id, String card) {
        jdbc.update("INSERT INTO \"transaction\" VALUES (?, '01', 1, 'POS TERM', 'SYNTHETIC', 12.34, 1, 'SHOP', "
                + "'CITY', '00000', ?, '2022-07-05 10:00:00.000000', '2022-07-05 10:00:00.000000', '')", id, card);
    }

    private ResultActions enter(String accountId, String confirm, String shownBalance) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("accountId", accountId);
        body.put("confirm", confirm);
        body.put("currentBalance", shownBalance == null ? null : new BigDecimal(shownBalance));
        return mvc.perform(post("/api/bill-payments").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk());
    }

    private BigDecimal balance(long id) {
        return jdbc.queryForObject("SELECT current_balance FROM account WHERE account_id = ?", BigDecimal.class, id);
    }

    private int transactions() {
        return jdbc.queryForObject("SELECT count(*) FROM \"transaction\"", Integer.class);
    }

    private void unchanged() {
        assertThat(balance(42)).isEqualByComparingTo("250.00");
        assertThat(transactions()).isEqualTo(1);
    }

    private void failInsert(String sqlState) {
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_write() RETURNS trigger AS $$ BEGIN "
                + "RAISE EXCEPTION 'synthetic' USING ERRCODE = '" + sqlState + "'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER fail_insert BEFORE INSERT ON \"transaction\" FOR EACH ROW EXECUTE FUNCTION fail_write()");
    }

    @Test void MAIN_PARA_menu_option_10_routes_to_CB00_and_requires_a_session() throws Exception {
        mvc.perform(post("/api/menus/main/select").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"option\":\"10\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("/bill-pay")).andExpect(jsonPath("$.tranId").value("CB00"))
                .andExpect(jsonPath("$.program").value("COBIL00C"));
        mvc.perform(post("/api/bill-payments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void PROCESS_ENTER_KEY_empty_account_is_checked_before_the_confirmation() throws Exception {
        for (String blank : new String[]{null, "", "           "}) {
            enter(blank, "x", "1.00").andExpect(jsonPath("$.message").value("Acct ID can NOT be empty..."))
                    .andExpect(jsonPath("$.severity").value("error")).andExpect(jsonPath("$.focus").value("accountId"))
                    .andExpect(jsonPath("$.confirm").value("x")).andExpect(jsonPath("$.currentBalance").value(1.00));
        }
        unchanged();
    }

    @Test void PROCESS_ENTER_KEY_other_confirm_value_is_rejected_without_reading_ACCTDAT() throws Exception {
        for (String value : new String[]{"X", "1", "YY"}) {
            enter("99999999999", value, "250.00")
                    .andExpect(jsonPath("$.message").value("Invalid value. Valid values are (Y/N)..."))
                    .andExpect(jsonPath("$.focus").value("confirm")).andExpect(jsonPath("$.accountId").value("99999999999"))
                    .andExpect(jsonPath("$.currentBalance").value(250.00));
        }
        unchanged();
    }

    @Test void CLEAR_CURRENT_SCREEN_on_N_resets_every_field_without_reading_ACCTDAT() throws Exception {
        for (String no : new String[]{"N", "n"}) {
            enter("99999999999", no, "250.00").andExpect(jsonPath("$.accountId").value(""))
                    .andExpect(jsonPath("$.confirm").value("")).andExpect(jsonPath("$.currentBalance").value(nullValue()))
                    .andExpect(jsonPath("$.message").value(nullValue())).andExpect(jsonPath("$.focus").value("accountId"));
        }
        unchanged();
    }

    @Test void READ_ACCTDAT_FILE_NOTFND_including_keys_that_are_not_eleven_digits() throws Exception {
        for (String id : new String[]{"99999999999", "42", "0000000004A", " 0000000042"}) {
            enter(id, "", "9.99").andExpect(jsonPath("$.message").value("Account ID NOT found..."))
                    .andExpect(jsonPath("$.focus").value("accountId")).andExpect(jsonPath("$.currentBalance").value(9.99));
        }
        enter("99999999999", "Y", null).andExpect(jsonPath("$.message").value("Account ID NOT found..."));
        unchanged();
    }

    @Test void READ_ACCTDAT_FILE_OTHER_when_the_record_is_held_for_update() throws Exception {
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            holder.createStatement().execute("SELECT * FROM account WHERE account_id = 42 FOR UPDATE");
            enter("00000000042", "Y", null).andExpect(jsonPath("$.message").value("Unable to lookup Account..."))
                    .andExpect(jsonPath("$.focus").value("accountId"));
            holder.rollback();
        }
        unchanged();
    }

    @Test void PROCESS_ENTER_KEY_nothing_to_pay_for_zero_or_credit_balance_shows_it() throws Exception {
        enter("00000000043", "", null).andExpect(jsonPath("$.message").value("You have nothing to pay..."))
                .andExpect(jsonPath("$.currentBalance").value(0.00)).andExpect(jsonPath("$.focus").value("accountId"));
        enter("00000000044", "Y", null).andExpect(jsonPath("$.message").value("You have nothing to pay..."))
                .andExpect(jsonPath("$.currentBalance").value(-10.00)).andExpect(jsonPath("$.confirm").value("Y"));
        assertThat(balance(44)).isEqualByComparingTo("-10.00");
        assertThat(transactions()).isEqualTo(1);
    }

    @Test void PROCESS_ENTER_KEY_blank_confirm_shows_the_balance_and_asks_for_confirmation() throws Exception {
        enter("00000000042", " ", null).andExpect(jsonPath("$.message").value("Confirm to make a bill payment..."))
                .andExpect(jsonPath("$.severity").value("error")).andExpect(jsonPath("$.focus").value("confirm"))
                .andExpect(jsonPath("$.currentBalance").value(250.00)).andExpect(jsonPath("$.accountId").value("00000000042"));
        unchanged();
    }

    @Test void WRITE_TRANSACT_FILE_and_UPDATE_ACCTDAT_FILE_pay_the_whole_balance() throws Exception {
        enter("00000000042", "y", "250.00")
                .andExpect(jsonPath("$.message").value("Payment successful.  Your Transaction ID is 0000000000000042."))
                .andExpect(jsonPath("$.severity").value("success")).andExpect(jsonPath("$.transactionId").value("0000000000000042"))
                .andExpect(jsonPath("$.accountId").value("")).andExpect(jsonPath("$.confirm").value(""))
                .andExpect(jsonPath("$.currentBalance").value(nullValue())).andExpect(jsonPath("$.focus").value("accountId"));
        assertThat(balance(42)).isEqualByComparingTo("0.00");
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM \"transaction\" WHERE transaction_id = '0000000000000042'");
        assertThat(row).containsEntry("transaction_type_code", "02").containsEntry("source", "POS TERM  ")
                .containsEntry("merchant_id", new BigDecimal("999999999")).containsEntry("card_number", "4000000000000421")
                .containsEntry("transaction_category_code", new BigDecimal("2"));
        assertThat(((String) row.get("description")).trim()).isEqualTo("BILL PAYMENT - ONLINE");
        assertThat(((String) row.get("merchant_name")).trim()).isEqualTo("BILL PAYMENT");
        assertThat(((String) row.get("merchant_city")).trim()).isEqualTo("N/A");
        assertThat(((String) row.get("merchant_zip_code")).trim()).isEqualTo("N/A");
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("250.00");
        assertThat((String) row.get("origination_timestamp"))
                .matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\\.000000")
                .isEqualTo(row.get("processed_timestamp"));
        assertThat(jdbc.queryForMap("SELECT current_cycle_credit, current_cycle_debit FROM account WHERE account_id = 42"))
                .containsEntry("current_cycle_credit", new BigDecimal("300.00"))
                .containsEntry("current_cycle_debit", new BigDecimal("-50.00"));
        enter("00000000042", "", null).andExpect(jsonPath("$.message").value("You have nothing to pay..."));
    }

    @Test void STARTBR_READPREV_TRANSACT_FILE_next_id_from_the_highest_key() throws Exception {
        jdbc.update("DELETE FROM \"transaction\"");
        enter("00000000045", "Y", null).andExpect(jsonPath("$.transactionId").value("0000000000000001"));
        transaction("9999999999999999", "4000000000000430");
        account(46, "1.00");
        jdbc.update("INSERT INTO card_xref VALUES ('4000000000000460', 7, 46, '') ON CONFLICT DO NOTHING");
        try {
            enter("00000000046", "Y", null).andExpect(jsonPath("$.transactionId").value("0000000000000000"))
                    .andExpect(jsonPath("$.message").value(matchesPattern(".*0000000000000000\\.")));
        } finally {
            jdbc.update("DELETE FROM card_xref WHERE account_id = 46");
            jdbc.update("DELETE FROM account WHERE account_id = 46");
        }
    }

    @Test void READPREV_TRANSACT_FILE_non_numeric_last_key_cannot_be_incremented() throws Exception {
        transaction("ABC0000000000000", "4000000000000430");
        enter("00000000042", "Y", null).andExpect(jsonPath("$.message").value("Unable to lookup Transaction..."))
                .andExpect(jsonPath("$.currentBalance").value(250.00)).andExpect(jsonPath("$.focus").value("accountId"));
        assertThat(balance(42)).isEqualByComparingTo("250.00");
        assertThat(transactions()).isEqualTo(2);
    }

    @Test void READ_CXACAIX_FILE_NOTFND_stops_before_writing() throws Exception {
        jdbc.update("DELETE FROM card_xref WHERE account_id = 42");
        enter("00000000042", "Y", null).andExpect(jsonPath("$.message").value("Account ID NOT found..."))
                .andExpect(jsonPath("$.currentBalance").value(250.00));
        unchanged();
    }

    @Test void WRITE_TRANSACT_FILE_DUPREC_rolls_back() throws Exception {
        failInsert("23505");
        enter("00000000042", "Y", null).andExpect(jsonPath("$.message").value("Tran ID already exist..."))
                .andExpect(jsonPath("$.focus").value("accountId")).andExpect(jsonPath("$.transactionId").value(nullValue()));
        unchanged();
    }

    @Test void WRITE_TRANSACT_FILE_OTHER_rolls_back() throws Exception {
        failInsert("P0001");
        enter("00000000042", "Y", null).andExpect(jsonPath("$.message").value("Unable to Add Bill pay Transaction..."));
        unchanged();
    }

    @Test void UPDATE_ACCTDAT_FILE_OTHER_rolls_back_the_transaction_write() throws Exception {
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_write() RETURNS trigger AS $$ BEGIN "
                + "RAISE EXCEPTION 'synthetic'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER fail_update BEFORE UPDATE ON account FOR EACH ROW EXECUTE FUNCTION fail_write()");
        enter("00000000042", "Y", null).andExpect(jsonPath("$.message").value("Unable to Update Account..."));
        unchanged();
    }
}
