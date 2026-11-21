package com.carddemo.account;

import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountViewService {
    public record Account(String activeStatus, BigDecimal currentBalance, BigDecimal creditLimit,
                          BigDecimal cashCreditLimit, String openDate, String expirationDate, String reissueDate,
                          BigDecimal currentCycleCredit, BigDecimal currentCycleDebit, String accountGroupId) { }

    public record Customer(String customerId, String firstName, String middleName, String lastName,
                           String addressLine1, String addressLine2, String addressLine3,
                           String stateCode, String countryCode, String zipCode, String phoneNumber1,
                           String phoneNumber2, String socialSecurityNumber, String governmentIssuedId,
                           String dateOfBirth, String electronicFundsTransferAccountId,
                           String primaryCardholderIndicator, String ficoCreditScore) { }

    public record Problem(String code, String message, boolean accountNumberInvalid) { }

    public record Screen(Account account, Customer customer, Problem error) {
        static Screen invalidAccount(String code, String message) {
            return new Screen(null, null, new Problem(code, message, true));
        }
    }

    private final JdbcClient jdbc;

    AccountViewService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Screen lookup(String accountId) {
        long key = Long.parseLong(accountId);
        var customerIds = jdbc.sql("SELECT customer_id FROM card_xref WHERE account_id = ? LIMIT 2")
                .param(key).query(Long.class).list();
        if (customerIds.isEmpty()) {
            return Screen.invalidAccount("XREF_NOT_FOUND", "Account:" + accountId + " not found in Cross ref file.");
        }
        if (customerIds.size() > 1) {
            return Screen.invalidAccount("XREF_DUPLICATE", "File Error: READ on CXACAIX returned RESP 15 REAS 140");
        }

        var account = jdbc.sql("""
                SELECT active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date,
                       reissue_date, current_cycle_credit, current_cycle_debit, rtrim(account_group_id) AS account_group_id
                FROM account WHERE account_id = ?
                """).param(key).query(Account.class).optional().orElse(null);
        var customer = jdbc.sql("""
                SELECT lpad(customer_id::text, 9, '0') AS customer_id,
                       rtrim(first_name) AS first_name, rtrim(middle_name) AS middle_name, rtrim(last_name) AS last_name,
                       rtrim(address_line_1) AS address_line1, rtrim(address_line_2) AS address_line2,
                       rtrim(address_line_3) AS address_line3, rtrim(state_code) AS state_code,
                       rtrim(country_code) AS country_code, rtrim(zip_code) AS zip_code,
                       rtrim(phone_number_1) AS phone_number1, rtrim(phone_number_2) AS phone_number2,
                       lpad(social_security_number::text, 9, '0') AS social_security_number,
                       rtrim(government_issued_id) AS government_issued_id, rtrim(date_of_birth) AS date_of_birth,
                       rtrim(electronic_funds_transfer_account_id) AS electronic_funds_transfer_account_id,
                       primary_cardholder_indicator, lpad(fico_credit_score::text, 3, '0') AS fico_credit_score
                FROM customer WHERE customer_id = ?
                """).param(customerIds.get(0)).query(Customer.class).optional().orElse(null);

        // The NOTFND flag in 9300 is commented out: still read the customer and keep the first error.
        Problem error = null;
        if (account == null) {
            error = new Problem("ACCOUNT_NOT_FOUND", "Account:" + accountId + " not found in Acct Master file.", true);
        } else if (customer == null) {
            error = new Problem("CUSTOMER_NOT_FOUND",
                    String.format(Locale.ROOT, "CustId:%09d not found in customer master.", customerIds.get(0)), false);
        }
        return new Screen(account, customer, error);
    }
}
