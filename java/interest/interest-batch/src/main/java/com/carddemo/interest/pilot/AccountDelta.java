package com.carddemo.interest.pilot;

import java.math.BigDecimal;

/** Pending REWRITE of one account: 1050-UPDATE-ACCOUNT (CBACT04C.cbl:350-369). */
public record AccountDelta(
        String acctId,
        BigDecimal oldCurrentBalance,
        BigDecimal newCurrentBalance,
        BigDecimal oldCycleCredit,
        BigDecimal newCycleCredit,
        BigDecimal oldCycleDebit,
        BigDecimal newCycleDebit) {

    /**
     * INT-R06 adds the account's interest to ACCT-CURR-BAL; INT-R07 zeroes both cycle totals
     * (INT-H09: TCATBALF balances are not cleared by this run).
     */
    public static AccountDelta closing(Account account, BigDecimal newCurrentBalance) {
        return new AccountDelta(account.acctId(),
                account.currentBalance(), newCurrentBalance,
                account.cycleCredit(), BigDecimal.ZERO.setScale(2),
                account.cycleDebit(), BigDecimal.ZERO.setScale(2));
    }
}
