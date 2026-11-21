package com.carddemo.batch.statements;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.carddemo.batch.common.VsamDataset;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.carddemo.batch.common.codec.RecordLayout;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads the PostgreSQL masters (business column names) back into copybook-named records. */
final class StatementStore implements StatementMasters {
    enum Master {
        ACCOUNT(VsamDataset.ACCOUNT, "account_id active_status current_balance credit_limit cash_credit_limit open_date expiration_date reissue_date current_cycle_credit current_cycle_debit zip_code account_group_id filler"),
        CUSTOMER(VsamDataset.CUSTOMER, "customer_id first_name middle_name last_name address_line_1 address_line_2 address_line_3 state_code country_code zip_code phone_number_1 phone_number_2 social_security_number government_issued_id date_of_birth electronic_funds_transfer_account_id primary_cardholder_indicator fico_credit_score filler"),
        CARD_XREF(VsamDataset.CARD_XREF, "card_number customer_id account_id filler"),
        TRANSACTION(VsamDataset.TRANSACTION, "transaction_id transaction_type_code transaction_category_code source description amount merchant_id merchant_name merchant_city merchant_zip_code card_number origination_timestamp processed_timestamp filler");

        final VsamDataset dataset;
        final String[] columns;

        Master(VsamDataset dataset, String columns) {
            this.dataset = dataset;
            this.columns = columns.split(" ");
        }
    }

    private final JdbcTemplate jdbc;

    StatementStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> crossReferences() throws Exception {
        return query(Master.CARD_XREF, "ORDER BY card_number COLLATE \"C\"");
    }

    @Override
    public Optional<Map<String, Object>> customer(BigDecimal customerId) throws Exception {
        return query(Master.CUSTOMER, "WHERE customer_id = ?", customerId).stream().findFirst();
    }

    @Override
    public Optional<Map<String, Object>> account(BigDecimal accountId) throws Exception {
        return query(Master.ACCOUNT, "WHERE account_id = ?", accountId).stream().findFirst();
    }

    /** TRANSACT KSDS unload in key (transaction ID) order, encoded as 350-byte CVTRA05Y records. */
    List<String> transactionRecords() throws Exception {
        var codec = new FixedRecordCodec(Master.TRANSACTION.dataset.layout.load());
        return query(Master.TRANSACTION, "ORDER BY transaction_id COLLATE \"C\"").stream()
                .map(values -> new String(codec.encode(values), StandardCharsets.ISO_8859_1)).toList();
    }

    private List<Map<String, Object>> query(Master master, String clause, Object... arguments) throws Exception {
        var layout = master.dataset.layout.load();
        return jdbc.query("SELECT " + String.join(",", master.columns) + " FROM \"" + master.dataset.table
                + "\" " + clause, (rows, rowNumber) -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    for (int i = 0; i < layout.fields().size(); i++) {
                        var field = layout.fields().get(i);
                        values.put(field.name(), field.kind() == RecordLayout.Kind.TEXT
                                ? rows.getString(i + 1) : rows.getBigDecimal(i + 1));
                    }
                    return values;
                }, arguments);
    }
}
