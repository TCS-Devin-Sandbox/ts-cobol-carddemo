package com.carddemo.batch.posting;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

final class PostingStore {
    private final JdbcTemplate jdbc;

    PostingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void openTransactionOutput() {
        jdbc.update("DELETE FROM \"transaction\"");
    }

    Long findAccountId(String cardNumber) {
        return jdbc.query("SELECT account_id FROM card_xref WHERE card_number = ?",
                rs -> rs.next() ? rs.getLong("account_id") : null, cardNumber);
    }

    Account findAccount(long id) {
        return jdbc.query("""
                SELECT credit_limit, current_cycle_credit, current_cycle_debit, expiration_date
                FROM account WHERE account_id = ? FOR UPDATE
                """, rs -> rs.next() ? new Account(rs.getBigDecimal("credit_limit"),
                rs.getBigDecimal("current_cycle_credit"), rs.getBigDecimal("current_cycle_debit"),
                rs.getString("expiration_date")) : null, id);
    }

    boolean addCategoryAmount(long accountId, String type, int category, BigDecimal amount) {
        int updated = jdbc.update("""
                UPDATE transaction_category_balance SET balance = mod(balance + ?, 1000000000)
                WHERE account_id = ? AND transaction_type_code = ? AND transaction_category_code = ?
                """, amount, accountId, type, category);
        if (updated != 0) return false;

        jdbc.update("""
                INSERT INTO transaction_category_balance
                    (account_id, transaction_type_code, transaction_category_code, balance, filler)
                VALUES (?, ?, ?, ?, '')
                """, accountId, type, category, amount);
        return true;
    }

    void addAccountAmount(long accountId, BigDecimal amount) {
        jdbc.update("""
                UPDATE account SET current_balance = mod(current_balance + ?, 10000000000),
                    current_cycle_credit = mod(current_cycle_credit + ?, 10000000000),
                    current_cycle_debit = mod(current_cycle_debit + ?, 10000000000)
                WHERE account_id = ?
                """, amount, amount.max(BigDecimal.ZERO), amount.min(BigDecimal.ZERO), accountId);
    }

    void writeTransaction(Map<String, Object> daily, String processedTimestamp) {
        jdbc.update("""
                INSERT INTO "transaction" (transaction_id, transaction_type_code, transaction_category_code,
                    source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip_code,
                    card_number, origination_timestamp, processed_timestamp, filler)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '')
                """, daily.get("DALYTRAN-ID"), daily.get("DALYTRAN-TYPE-CD"), daily.get("DALYTRAN-CAT-CD"),
                daily.get("DALYTRAN-SOURCE"), daily.get("DALYTRAN-DESC"), daily.get("DALYTRAN-AMT"),
                daily.get("DALYTRAN-MERCHANT-ID"), daily.get("DALYTRAN-MERCHANT-NAME"),
                daily.get("DALYTRAN-MERCHANT-CITY"), daily.get("DALYTRAN-MERCHANT-ZIP"),
                daily.get("DALYTRAN-CARD-NUM"), daily.get("DALYTRAN-ORIG-TS"), processedTimestamp);
    }

    record Account(BigDecimal creditLimit, BigDecimal cycleCredit, BigDecimal cycleDebit, String expirationDate) { }
}
