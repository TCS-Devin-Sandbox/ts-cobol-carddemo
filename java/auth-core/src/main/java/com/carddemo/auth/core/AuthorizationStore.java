package com.carddemo.auth.core;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

final class AuthorizationStore {
    private final JdbcTemplate jdbc;

    AuthorizationStore(DataSource source) {
        jdbc = new JdbcTemplate(source);
    }

    record Master(long accountId, long customerId, BigDecimal creditLimit, BigDecimal cashLimit,
                  BigDecimal currentBalance, boolean customerFound) {
    }

    record Summary(BigDecimal creditLimit, BigDecimal creditBalance) {
        BigDecimal available() {
            return creditLimit.subtract(creditBalance);
        }
    }

    Master findMaster(String card) {
        return jdbc.query("""
                SELECT account_id, customer_id FROM card_xref WHERE card_number = ?
                """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)}, card)
                .stream().findFirst().map(ids -> {
                    var account = jdbc.query("""
                            SELECT credit_limit, cash_credit_limit, current_balance FROM account
                            WHERE account_id = ? FOR UPDATE
                            """, (rs, row) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2),
                            rs.getBigDecimal(3)}, ids[0]).stream().findFirst().orElse(null);
                    boolean customer = Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT EXISTS (SELECT 1 FROM customer WHERE customer_id = ?)", Boolean.class, ids[1]));
                    return new Master(ids[0], ids[1], account == null ? AuthRequest.ZERO : account[0],
                            account == null ? AuthRequest.ZERO : account[1], account == null ? null : account[2],
                            customer);
                }).orElse(null);
    }

    Summary findSummary(long accountId) {
        return jdbc.query("""
                SELECT credit_limit, credit_balance FROM pending_auth_summary WHERE account_id = ? FOR UPDATE
                """, (rs, row) -> new Summary(rs.getBigDecimal(1), rs.getBigDecimal(2)), accountId)
                .stream().findFirst().orElse(null);
    }

    BigDecimal writeSummary(Master master, Summary summary, AuthorizationService.Decision decision,
                            BigDecimal previousDetailAmount) {
        int approvedCount = decision.approved() ? 1 : 0;
        int declinedCount = 1 - approvedCount;
        BigDecimal approved = decision.approvedAmount();
        BigDecimal declined = decision.approved() ? AuthRequest.ZERO : previousDetailAmount;
        BigDecimal balance = (summary == null ? AuthRequest.ZERO : summary.creditBalance()).add(approved);
        if (summary == null) {
            jdbc.update("""
                    INSERT INTO pending_auth_summary (account_id, customer_id, auth_status, account_status_1,
                      account_status_2, account_status_3, account_status_4, account_status_5, credit_limit,
                      cash_limit, credit_balance, cash_balance, approved_auth_count, declined_auth_count,
                      approved_auth_amount, declined_auth_amount, filler)
                    VALUES (?, ?, ' ', ' ', ' ', ' ', ' ', ' ', ?, ?, ?, 0, ?, ?, ?, ?, ' ')
                    """, master.accountId(), master.customerId(), master.creditLimit(), master.cashLimit(),
                    balance, approvedCount, declinedCount, approved, declined);
        } else {
            jdbc.update("""
                    UPDATE pending_auth_summary SET credit_limit = ?, cash_limit = ?, credit_balance = ?,
                      cash_balance = CASE WHEN ? THEN 0 ELSE cash_balance END,
                      approved_auth_count = approved_auth_count + ?, declined_auth_count = declined_auth_count + ?,
                      approved_auth_amount = approved_auth_amount + ?, declined_auth_amount = declined_auth_amount + ?
                    WHERE account_id = ?
                    """, master.creditLimit(), master.cashLimit(), balance, decision.approved(), approvedCount,
                    declinedCount, approved, declined, master.accountId());
        }
        return master.creditLimit().subtract(balance);
    }

    void insertDetail(long accountId, AuthRequest r, AuthorizationService.Decision decision, LocalDateTime now) {
        int date = Math.floorMod(now.getYear(), 100) * 1000 + now.getDayOfYear();
        int time = now.getHour() * 10000000 + now.getMinute() * 100000 + now.getSecond() * 1000
                + now.getNano() / 1000000;
        jdbc.update("""
                INSERT INTO pending_auth_detail (account_id, auth_date_inv, auth_time_inv, auth_original_date,
                  auth_original_time, card_number, auth_type, card_expiry_date, message_type, message_source,
                  auth_id_code, auth_response_code, auth_response_reason, processing_code, transaction_amount,
                  approved_amount, merchant_category_code, acquirer_country_code, pos_entry_mode, merchant_id,
                  merchant_name, merchant_city, merchant_state, merchant_zip, transaction_id, match_status,
                  fraud_status, fraud_report_date, filler)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ' ', ' ', ' ')
                """, accountId, 99999 - date, 999999999 - time, r.date(), r.time(), r.card(), r.type(), r.expiry(),
                r.messageType(), r.messageSource(), r.time(), decision.code(), decision.reason(), r.processingCode(),
                r.amount(), decision.approvedAmount(), r.category(), r.country(), r.entryMode(), r.merchantId(),
                r.merchantName(), r.merchantCity(), r.merchantState(), r.merchantZip(), r.transactionId(),
                decision.approved() ? "P" : "D");
    }
}
