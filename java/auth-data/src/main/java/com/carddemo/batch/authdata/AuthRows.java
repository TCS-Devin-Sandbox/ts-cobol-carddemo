package com.carddemo.batch.authdata;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.nCopies;

enum AuthRows {
    SUMMARY("pending_auth_summary", """
            account_id:PA-ACCT-ID customer_id:PA-CUST-ID auth_status:PA-AUTH-STATUS
            account_status_1:PA-ACCOUNT-STATUS[1] account_status_2:PA-ACCOUNT-STATUS[2]
            account_status_3:PA-ACCOUNT-STATUS[3] account_status_4:PA-ACCOUNT-STATUS[4]
            account_status_5:PA-ACCOUNT-STATUS[5] credit_limit:PA-CREDIT-LIMIT cash_limit:PA-CASH-LIMIT
            credit_balance:PA-CREDIT-BALANCE cash_balance:PA-CASH-BALANCE
            approved_auth_count:PA-APPROVED-AUTH-CNT declined_auth_count:PA-DECLINED-AUTH-CNT
            approved_auth_amount:PA-APPROVED-AUTH-AMT declined_auth_amount:PA-DECLINED-AUTH-AMT filler:FILLER@66
            """),
    DETAIL("pending_auth_detail", """
            auth_date_inv:PA-AUTH-DATE-9C auth_time_inv:PA-AUTH-TIME-9C
            auth_original_date:PA-AUTH-ORIG-DATE auth_original_time:PA-AUTH-ORIG-TIME card_number:PA-CARD-NUM
            auth_type:PA-AUTH-TYPE card_expiry_date:PA-CARD-EXPIRY-DATE message_type:PA-MESSAGE-TYPE
            message_source:PA-MESSAGE-SOURCE auth_id_code:PA-AUTH-ID-CODE auth_response_code:PA-AUTH-RESP-CODE
            auth_response_reason:PA-AUTH-RESP-REASON processing_code:PA-PROCESSING-CODE
            transaction_amount:PA-TRANSACTION-AMT approved_amount:PA-APPROVED-AMT
            merchant_category_code:PA-MERCHANT-CATAGORY-CODE acquirer_country_code:PA-ACQR-COUNTRY-CODE
            pos_entry_mode:PA-POS-ENTRY-MODE merchant_id:PA-MERCHANT-ID merchant_name:PA-MERCHANT-NAME
            merchant_city:PA-MERCHANT-CITY merchant_state:PA-MERCHANT-STATE merchant_zip:PA-MERCHANT-ZIP
            transaction_id:PA-TRANSACTION-ID match_status:PA-MATCH-STATUS fraud_status:PA-AUTH-FRAUD
            fraud_report_date:PA-FRAUD-RPT-DATE filler:FILLER@183
            """);

    private static final char LOW_VALUE = '\u2400';
    private record Field(String column, String copybook) {}
    final String table;
    private final List<Field> fields;

    AuthRows(String table, String mapping) {
        this.table = table;
        fields = List.of(mapping.strip().split("\\s+")).stream().map(pair -> {
            String[] names = pair.split(":");
            return new Field(names[0], names[1]);
        }).toList();
    }

    PreparedStatement insert(Connection db) throws SQLException {
        String columns = fields.stream().map(Field::column).collect(Collectors.joining(", "));
        int size = fields.size();
        if (this == DETAIL) {
            columns = "account_id, " + columns;
            size++;
        }
        return db.prepareStatement("INSERT INTO " + table + " (" + columns + ") VALUES ("
                + String.join(", ", nCopies(size, "?")) + ")");
    }

    void write(PreparedStatement insert, BigDecimal account, Map<String, Object> values) throws SQLException {
        int index = 1;
        if (this == DETAIL) insert.setBigDecimal(index++, account);
        for (Field field : fields) {
            Object value = values.get(field.copybook());
            if (value instanceof String text) insert.setString(index++, text.replace('\0', LOW_VALUE));
            else if (value instanceof BigDecimal number) insert.setBigDecimal(index++, number);
            else throw new IllegalArgumentException("Missing or non-relational field: " + field.copybook());
        }
        insert.executeUpdate();
    }

    String projection(String alias) {
        return fields.stream().map(f -> alias + "." + f.column() + " AS " + alias + "_" + f.column())
                .collect(Collectors.joining(", "));
    }

    Map<String, Object> read(ResultSet row, String prefix) throws SQLException {
        var values = new LinkedHashMap<String, Object>();
        for (Field field : fields) {
            Object value = row.getObject(prefix + field.column());
            if (value instanceof String text) value = text.replace(LOW_VALUE, '\0');
            if (value instanceof Number number && !(value instanceof BigDecimal)) {
                value = new BigDecimal(number.toString());
            }
            values.put(field.copybook(), value);
        }
        return values;
    }
}
