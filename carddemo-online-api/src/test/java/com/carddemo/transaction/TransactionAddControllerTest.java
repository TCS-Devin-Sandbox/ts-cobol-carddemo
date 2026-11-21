package com.carddemo.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class TransactionAddControllerTest {
    JdbcTemplate jdbc;
    TransactionAddController controller;
    Map<String, String> fields;
    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class);
        controller = new TransactionAddController(jdbc);
        fields = new LinkedHashMap<>(Map.of("account_id", "00000000001", "confirmation", ""));
        String[] values = {"01", "0001", "TEST", "SYNTHETIC", "+00000001.23", "2022-07-05", "2022-07-05", "000000001", "SHOP", "CITY", "00000"};
        for (int i = 0; i < values.length; i++) fields.put(TransactionAddController.KEYS[i], values[i]);
        when(jdbc.queryForList(contains("card_xref"), any(Object.class))).thenReturn(List.of(Map.of("account_id", BigDecimal.ONE, "card_number", "0000000000000001")));
        when(jdbc.queryForList(contains("transaction_id FROM"))).thenReturn(List.of(Map.of("transaction_id", "0000000000000042")));
    }
    @Test void PROCESS_ENTER_KEY_confirm_and_ADD_TRANSACTION_WRITE_TRANSACT_FILE() {
        assertThat(controller.enter(fields, false).message()).isEqualTo("Confirm to add this transaction...");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        fields.put("confirmation", "y");
        var result = controller.enter(fields, false);
        assertThat(result.added()).isTrue();
        assertThat(result.message()).isEqualTo("Transaction added successfully.  Your Tran ID is 0000000000000043.");
        assertThat(result.fields()).isEmpty();
    }
    @Test void STARTBR_READPREV_ENDBR_empty_starts_at_one() {
        when(jdbc.queryForList(contains("transaction_id FROM"))).thenReturn(List.of());
        fields.put("confirmation", "Y");
        assertThat(controller.enter(fields, false).message()).contains("0000000000000001");
    }
    @Test void READ_CXACAIX_READ_CCXREF_account_wins_and_card_only() {
        fields.put("card_number", "garbage");
        assertThat(controller.enter(fields, false).fields().get("card_number")).isEqualTo("0000000000000001");
        fields.put("account_id", ""); fields.put("card_number", "0000000000000001");
        assertThat(controller.enter(fields, false).fields().get("account_id")).isEqualTo("00000000001");
        when(jdbc.queryForList(contains("card_xref"), any(Object.class))).thenReturn(List.of());
        assertThat(controller.enter(fields, false).message()).isEqualTo("Card Number NOT found...");
    }
    @ParameterizedTest @CsvSource({"account_id,abc,Account ID must be Numeric...", "transaction_type_code,xx,Type CD must be Numeric...", "transaction_category_code,xxxx,Category CD must be Numeric...", "amount,1.23,Amount should be in format -99999999.99", "origination_timestamp,2022/01/01,Orig Date should be in format YYYY-MM-DD", "processed_timestamp,2022-02-29,Proc Date - Not a valid date...", "merchant_id,abc,Merchant ID must be Numeric...", "confirmation,X,Invalid value. Valid values are (Y/N)..."})
    void VALIDATE_INPUT_KEY_FIELDS_VALIDATE_INPUT_DATA_FIELDS(String field, String value, String message) {
        fields.put(field, value);
        assertThat(controller.enter(fields, false).message()).isEqualTo(message);
    }
    @Test void VALIDATE_INPUT_DATA_FIELDS_all_required_in_order() {
        for (int i = 0; i < TransactionAddController.KEYS.length; i++) {
            String key = TransactionAddController.KEYS[i]; String previous = fields.put(key, "");
            assertThat(controller.enter(fields, false).message()).isEqualTo(TransactionAddController.LABELS[i] + " can NOT be empty...");
            fields.put(key, previous);
        }
    }
    @Test void CSUTLDTC_2513_range_ignored_but_invalid_calendar_rejected() {
        fields.put("origination_timestamp", "1500-01-01");
        assertThat(controller.enter(fields, false).field()).isEqualTo("confirmation");
        fields.put("origination_timestamp", "2022-02-29");
        assertThat(controller.enter(fields, false).message()).isEqualTo("Orig Date - Not a valid date...");
    }
    @Test void COPY_LAST_TRAN_DATA_empty_and_lookup_error() {
        when(jdbc.queryForList(contains("SELECT * FROM \"transaction\""))).thenReturn(List.of());
        assertThat(controller.enter(fields, true).fields().get("description")).isEmpty();
        when(jdbc.queryForList(contains("SELECT * FROM \"transaction\""))).thenThrow(new DataAccessResourceFailureException("test"));
        assertThat(controller.enter(fields, true).message()).isEqualTo("Unable to lookup Transaction...");
    }
    @Test void COPY_LAST_TRAN_DATA_global_not_account_filtered_and_preserves_confirmation() {
        var row = new LinkedHashMap<String, Object>(fields);
        row.put("amount", new BigDecimal("-2.55"));
        row.put("transaction_category_code", BigDecimal.ONE);
        row.put("merchant_id", BigDecimal.TEN);
        row.put("description", "X".repeat(100));
        when(jdbc.queryForList(contains("SELECT * FROM \"transaction\""))).thenReturn(List.of(row));
        var result = controller.enter(fields, true);
        assertThat(result.fields().get("amount")).isEqualTo("-00000002.55");
        assertThat(result.fields().get("merchant_id")).isEqualTo("000000010");
        assertThat(result.fields().get("description")).hasSize(60);
        assertThat(result.fields().get("card_number")).isEqualTo("0000000000000001");
    }
    @Test void READ_CXACAIX_READ_CCXREF_errors_and_missing() {
        when(jdbc.queryForList(contains("card_xref"), any(Object.class))).thenReturn(List.of());
        assertThat(controller.enter(fields, false).message()).isEqualTo("Account ID NOT found...");
        when(jdbc.queryForList(contains("card_xref"), any(Object.class))).thenThrow(new DataAccessResourceFailureException("test"));
        assertThat(controller.enter(fields, false).message()).isEqualTo("Unable to lookup Acct in XREF AIX file...");
        fields.put("account_id", ""); fields.put("card_number", "0000000000000001");
        assertThat(controller.enter(fields, false).message()).isEqualTo("Unable to lookup Card # in XREF file...");
        fields.put("card_number", "x");
        assertThat(controller.enter(fields, false).message()).isEqualTo("Card Number must be Numeric...");
        fields.put("card_number", "");
        assertThat(controller.enter(fields, false).message()).isEqualTo("Account or Card Number must be entered...");
    }
    @Test void STARTBR_READPREV_and_WRITE_TRANSACT_FILE_errors() {
        fields.put("confirmation", "Y");
        when(jdbc.queryForList(contains("transaction_id FROM"))).thenThrow(new DataAccessResourceFailureException("test"));
        assertThat(controller.enter(fields, false).message()).isEqualTo("Unable to lookup Transaction...");
        when(jdbc.queryForList(contains("transaction_id FROM"))).thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DuplicateKeyException("test"));
        assertThat(controller.enter(fields, false).message()).isEqualTo("Tran ID already exist...");
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DataAccessResourceFailureException("test"));
        assertThat(controller.enter(fields, false).message()).isEqualTo("Unable to Add Transaction...");
    }
}
