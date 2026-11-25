package com.carddemo.auth.core;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Consumer;
import javax.sql.DataSource;

import com.carddemo.auth.core.AuthorizationStore.Account;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class AuthorizationService {
    private final AuthorizationStore store;
    private final TransactionTemplate database;
    private final TransactionTemplate noSyncpoint;
    private final Clock clock;

    public AuthorizationService(DataSource source, Clock clock) {
        store = new AuthorizationStore(source);
        var manager = new JdbcTransactionManager(source);
        manager.setTransactionSynchronization(AbstractPlatformTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
        database = new TransactionTemplate(manager);
        database.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        noSyncpoint = new TransactionTemplate(manager);
        noSyncpoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.clock = Objects.requireNonNull(clock);
    }

    public Invocation newInvocation() {
        return new Invocation();
    }

    public final class Invocation {
        private BigDecimal availableFromSummary;
        private BigDecimal previousDetailAmount = AuthRequest.ZERO;
        private Account accountWorkArea = new Account(AuthRequest.ZERO, AuthRequest.ZERO, AuthRequest.ZERO);

        private Invocation() {
        }

        /**
         * Processes one message in a serial CP00 invocation. The reply sender must send synchronously
         * without an MQ syncpoint; failures propagate. Discard this invocation after a processing failure.
         */
        public void process(String csv, Consumer<byte[]> sendReply) {
            AuthRequest request = AuthRequest.parse(csv);
            State next = database.execute(status -> {
                var master = store.findMaster(request.card());
                var summary = master == null ? null : store.findSummary(master.accountId());
                BigDecimal available = master == null ? availableFromSummary
                        : summary == null ? null : summary.available();
                Decision decision = decide(request.amount(), available, master);
                noSyncpoint.executeWithoutResult(ignored ->
                        sendReply.accept(request.reply(decision.code(), decision.reason(), decision.approvedAmount())));
                if (master == null) {
                    return new State(available, previousDetailAmount, accountWorkArea);
                }
                Account nextAccount = master.account() == null ? accountWorkArea : master.account();
                BigDecimal nextAvailable = store.writeSummary(master, nextAccount, summary, decision, previousDetailAmount);
                store.insertDetail(master.accountId(), request, decision, LocalDateTime.now(clock));
                return new State(summary == null ? null : nextAvailable, request.amount(), nextAccount);
            });
            availableFromSummary = next.available();
            previousDetailAmount = next.detailAmount();
            accountWorkArea = next.account();
        }
    }

    private record State(BigDecimal available, BigDecimal detailAmount, Account account) {
    }

    record Decision(boolean approved, String reason, BigDecimal approvedAmount) {
        String code() {
            return approved ? "00" : "05";
        }
    }

    static Decision decide(BigDecimal amount, BigDecimal summaryAvailable, AuthorizationStore.Master master) {
        Account account = master == null ? null : master.account();
        boolean accountFound = account != null;
        BigDecimal available = summaryAvailable != null ? summaryAvailable
                : accountFound ? account.creditLimit().subtract(account.currentBalance()) : null;
        if (available != null && amount.compareTo(available) <= 0) {
            return new Decision(true, "0000", amount);
        }
        String reason = accountFound && master.customerFound() ? "4100" : "3100";
        return new Decision(false, reason, AuthRequest.ZERO);
    }
}
