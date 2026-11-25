package com.carddemo.authorization;

import com.carddemo.account.AccountViewService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPendingAuthorizations implements PendingAuthorizations {
    private final JdbcClient jdbc;
    private final AccountViewService accounts;

    JdbcPendingAuthorizations(JdbcClient jdbc, AccountViewService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    /** GATHER-ACCOUNT-DETAILS over the CAVW lookup; the last NOTFND message wins, as in WS-MESSAGE. */
    @Override
    public AccountLookup account(String accountId) {
        var screen = accounts.lookup(accountId);
        var code = screen.error() == null ? "" : screen.error().code();
        if (code.equals("XREF_NOT_FOUND")) {
            return new AccountLookup(null,
                    "Account:" + accountId + " not found in XREF file. Resp:000000013 Reas:000000080", false);
        }
        if (code.equals("XREF_DUPLICATE")) {
            return new AccountLookup(null,
                    "Account:" + accountId + " System error while reading XREF file. Resp:000000015 Reas:000000140",
                    true);
        }
        var account = screen.account();
        var customer = screen.customer();
        String message = null;
        if (account == null) {
            message = "Account:" + accountId + " not found in ACCT file. Resp:000000013 Reas:000000080";
        }
        if (customer == null) {
            long customerId = jdbc.sql("SELECT customer_id FROM card_xref WHERE account_id = ?")
                    .param(Long.parseLong(accountId)).query(Long.class).single();
            message = String.format(Locale.ROOT,
                    "Customer:%09d not found in CUST file. Resp:000000013 Reas:000000080", customerId);
        }
        var section = new Account(
                customer == null ? null : customer.customerId(),
                customer == null ? null : name(customer.firstName(), customer.middleName(), customer.lastName()),
                customer == null ? null : upTo(customer.addressLine1()) + "," + upTo(customer.addressLine2()),
                customer == null ? null : upTo(customer.addressLine3()) + "," + pad(customer.stateCode(), 2) + ","
                        + pad(customer.zipCode(), 5).substring(0, 5),
                customer == null ? null : customer.phoneNumber1(),
                account == null ? null : account.creditLimit(),
                account == null ? null : account.cashCreditLimit());
        return new AccountLookup(section, message, false);
    }

    /** CNAMEO: first and last name DELIMITED BY SPACES around the middle initial. */
    static String name(String first, String middle, String last) {
        return firstWord(first) + " " + pad(middle, 1).charAt(0) + " " + firstWord(last);
    }

    private static String firstWord(String value) {
        var text = value == null ? "" : value;
        int space = text.indexOf(' ');
        return space < 0 ? text : text.substring(0, space);
    }

    /** DELIMITED BY '  ': the text before the first double space. */
    static String upTo(String value) {
        var text = value == null ? "" : value;
        int gap = text.indexOf("  ");
        return gap < 0 ? text : text.substring(0, gap);
    }

    private static String pad(String value, int length) {
        var text = value == null ? "" : value;
        return text.length() >= length ? text : text + " ".repeat(length - text.length());
    }

    @Override
    public Optional<Summary> summary(long accountId) {
        return jdbc.sql("""
                SELECT approved_auth_count, declined_auth_count, credit_balance, cash_balance,
                       approved_auth_amount, declined_auth_amount
                FROM pending_auth_summary WHERE account_id = ?
                """).param(accountId).query((rs, n) -> new Summary(true, rs.getInt(1), rs.getInt(2),
                        rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6)))
                .optional();
    }

    @Override
    public List<Row> details(long accountId, AuthorizationKey from, boolean inclusive, int limit) {
        var where = from == null ? "" : inclusive
                ? " AND (auth_date_inv, auth_time_inv) >= (:date, :time)"
                : " AND (auth_date_inv, auth_time_inv) > (:date, :time)";
        var query = jdbc.sql("""
                SELECT auth_date_inv, auth_time_inv, rtrim(transaction_id), auth_original_date, auth_original_time,
                       rtrim(auth_type), auth_response_code, match_status, approved_amount
                FROM pending_auth_detail WHERE account_id = :account"""
                + where + " ORDER BY auth_date_inv, auth_time_inv LIMIT :limit")
                .param("account", accountId).param("limit", limit);
        if (from != null) {
            query = query.param("date", from.dateInverted()).param("time", from.timeInverted());
        }
        return query.query((rs, n) -> row(new AuthorizationKey(rs.getInt(1), rs.getLong(2)), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getBigDecimal(9))).list();
    }

    /** POPULATE-AUTH-LIST: YYMMDD as MM/DD/YY, HHMMSS as HH:MM:SS, response '00' approves. */
    static Row row(AuthorizationKey key, String transactionId, String date, String time, String type,
                   String responseCode, String matchStatus, BigDecimal approvedAmount) {
        return new Row(key.toString(), transactionId,
                date.substring(2, 4) + "/" + date.substring(4, 6) + "/" + date.substring(0, 2),
                time.substring(0, 2) + ":" + time.substring(2, 4) + ":" + time.substring(4, 6),
                type, "00".equals(responseCode) ? "A" : "D", matchStatus, approvedAmount);
    }
}
