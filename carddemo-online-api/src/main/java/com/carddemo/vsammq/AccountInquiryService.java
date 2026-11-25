package com.carddemo.vsammq;

import com.carddemo.vsammq.AccountInquiryReply.Account;
import com.carddemo.vsammq.AccountInquiryReply.Request;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CDRA / COACCT01 4000-PROCESS-REQUEST-REPLY: read ACCTDAT by key and build the reply message. */
@Service
public class AccountInquiryService {
    public enum Outcome { FOUND, NOT_FOUND, INVALID_REQUEST }

    public record Reply(Outcome outcome, String message) { }

    private final JdbcClient jdbc;

    AccountInquiryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Reply process(Request request) {
        if (!request.isInquiry()) {
            return new Reply(Outcome.INVALID_REQUEST, AccountInquiryReply.invalid(request));
        }
        return jdbc.sql("""
                        SELECT account_id, active_status, current_balance, credit_limit, cash_credit_limit, open_date,
                               expiration_date, reissue_date, current_cycle_credit, current_cycle_debit, account_group_id
                        FROM account WHERE account_id = ?
                        """)
                .param(request.accountId()).query(Account.class).optional()
                .map(a -> new Reply(Outcome.FOUND, AccountInquiryReply.found(a)))
                .orElseGet(() -> new Reply(Outcome.NOT_FOUND, AccountInquiryReply.notFound(request)));
    }
}
