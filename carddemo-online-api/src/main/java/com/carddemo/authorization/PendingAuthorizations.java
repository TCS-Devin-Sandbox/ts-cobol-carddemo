package com.carddemo.authorization;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** The reads COPAUS0C makes: CXACAIX/ACCTDAT/CUSTDAT, GU PAUTSUM0 and GNP PAUTDTL1 in key order. */
interface PendingAuthorizations {
    record Account(String customerId, String name, String addressLine1, String addressLine2, String phoneNumber1,
                   BigDecimal creditLimit, BigDecimal cashCreditLimit) { }

    /** Account section plus the message the last failing read leaves in WS-MESSAGE. */
    record AccountLookup(Account account, String message, boolean error) { }

    record Summary(boolean found, int approvedCount, int declinedCount, BigDecimal creditBalance,
                   BigDecimal cashBalance, BigDecimal approvedAmount, BigDecimal declinedAmount) {
        static final Summary ABSENT = new Summary(false, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    record Row(String authorizationKey, String transactionId, String date, String time, String type,
               String approval, String matchStatus, BigDecimal approvedAmount) { }

    AccountLookup account(String accountId);

    Optional<Summary> summary(long accountId);

    /** Details of one root in PAUT9CTS order, starting at {@code from} (all when null). */
    List<Row> details(long accountId, AuthorizationKey from, boolean inclusive, int limit);
}
