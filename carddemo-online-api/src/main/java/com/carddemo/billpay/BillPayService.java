package com.carddemo.billpay;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** COBIL00C PROCESS-ENTER-KEY: account read, confirmation, TRANSACT write and ACCTDAT rewrite in one unit of work. */
@Service
public class BillPayService {
    private static final Pattern ACCOUNT_KEY = Pattern.compile("[0-9]{11}");
    private static final Pattern TRANSACTION_KEY = Pattern.compile("[0-9]{16}");
    private static final BigInteger TRANSACTION_ID_MODULUS = BigInteger.TEN.pow(16);
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss'.000000'", Locale.ROOT);

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    BillPayService(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        this(jdbc, transactionManager, Clock.systemDefaultZone());
    }

    BillPayService(JdbcClient jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public record Payment(String accountId, String confirm, BigDecimal currentBalance) { }

    public record Screen(String accountId, BigDecimal currentBalance, String confirm, String message,
                         String severity, String focus, String transactionId) { }

    public Screen process(Payment in) {
        String accountId = in.accountId() == null ? "" : in.accountId();
        String confirm = in.confirm() == null ? "" : in.confirm();
        if (accountId.isBlank()) {
            return error(in, in.currentBalance(), "Acct ID can NOT be empty...", "accountId");
        }
        boolean pay;
        switch (confirm) {
            case "Y", "y" -> pay = true;
            case "N", "n" -> { return cleared(null, null, null); }
            default -> {
                if (!confirm.isBlank()) {
                    return error(in, in.currentBalance(), "Invalid value. Valid values are (Y/N)...", "confirm");
                }
                pay = false;
            }
        }
        // ACTIDIN is X(11) moved to ACCT-ID 9(11): anything but eleven digits is a key no record carries.
        if (!ACCOUNT_KEY.matcher(accountId).matches()) {
            return error(in, in.currentBalance(), "Account ID NOT found...", "accountId");
        }
        return transactions.execute(status -> enter(in, Long.parseLong(accountId), pay, status));
    }

    private Screen enter(Payment in, long accountId, boolean pay, TransactionStatus status) {
        BigDecimal balance;
        try {
            List<BigDecimal> rows = jdbc.sql("SELECT current_balance FROM account WHERE account_id = ? FOR UPDATE")
                    .param(accountId).query(BigDecimal.class).list();
            if (rows.isEmpty()) return error(in, in.currentBalance(), "Account ID NOT found...", "accountId");
            balance = rows.get(0);
        } catch (DataAccessException e) {
            status.setRollbackOnly();
            return error(in, in.currentBalance(), "Unable to lookup Account...", "accountId");
        }
        if (balance.signum() <= 0) return error(in, balance, "You have nothing to pay...", "accountId");
        if (!pay) return error(in, balance, "Confirm to make a bill payment...", "confirm");

        String failure = null;
        String transactionId = null;
        try {
            failure = "Unable to lookup XREF AIX file...";
            // CXACAIX is a non-unique path: READ returns the first card in key order (DUPKEY) and the payment goes on.
            List<String> cards = jdbc.sql("SELECT card_number FROM card_xref WHERE account_id = ? "
                    + "ORDER BY card_number COLLATE \"C\" LIMIT 1").param(accountId).query(String.class).list();
            if (cards.isEmpty()) return rollback(status, in, balance, "Account ID NOT found...");

            failure = "Unable to lookup Transaction...";
            jdbc.sql("SELECT pg_advisory_xact_lock(hashtext('TRANSACT'))").query().singleRow();
            String last = jdbc.sql("SELECT max(transaction_id COLLATE \"C\") FROM \"transaction\"")
                    .query(String.class).optional().orElse(null);
            if (last != null && !TRANSACTION_KEY.matcher(last).matches()) return rollback(status, in, balance, failure);
            BigInteger next = (last == null ? BigInteger.ZERO : new BigInteger(last)).add(BigInteger.ONE)
                    .mod(TRANSACTION_ID_MODULUS);
            transactionId = String.format(Locale.ROOT, "%016d", next);

            failure = "Unable to Add Bill pay Transaction...";
            String now = TIMESTAMP.format(LocalDateTime.now(clock));
            jdbc.sql("""
                    INSERT INTO "transaction" (transaction_id, transaction_type_code, transaction_category_code,
                        source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip_code,
                        card_number, origination_timestamp, processed_timestamp, filler)
                    VALUES (?, '02', 2, 'POS TERM', 'BILL PAYMENT - ONLINE', ?, 999999999, 'BILL PAYMENT',
                        'N/A', 'N/A', ?, ?, ?, '')
                    """).params(transactionId, balance, cards.get(0), now, now).update();

            failure = "Unable to Update Account...";
            int updated = jdbc.sql("UPDATE account SET current_balance = current_balance - ? WHERE account_id = ?")
                    .params(balance, accountId).update();
            if (updated != 1) return rollback(status, in, balance, "Account ID NOT found...");
        } catch (DuplicateKeyException e) {
            return rollback(status, in, balance, transactionId != null && failure.startsWith("Unable to Add")
                    ? "Tran ID already exist..." : failure);
        } catch (DataAccessException e) {
            return rollback(status, in, balance, failure);
        }
        return cleared("Payment successful.  Your Transaction ID is " + transactionId + ".", "success", transactionId);
    }

    private static Screen rollback(TransactionStatus status, Payment in, BigDecimal balance, String message) {
        status.setRollbackOnly();
        return error(in, balance, message, "accountId");
    }

    private static Screen error(Payment in, BigDecimal balance, String message, String focus) {
        return new Screen(in.accountId(), balance, in.confirm(), message, "error", focus, null);
    }

    private static Screen cleared(String message, String severity, String transactionId) {
        return new Screen("", null, "", message, severity, "accountId", transactionId);
    }
}
