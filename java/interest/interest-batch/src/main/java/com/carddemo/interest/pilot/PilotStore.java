package com.carddemo.interest.pilot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** All SQL against the private {@code interest_pilot} tables. Every statement is scoped to one run. */
@Repository
public class PilotStore {

    private final JdbcTemplate jdbc;

    public PilotStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertAccounts(UUID runId, List<Account> accounts) {
        jdbc.batchUpdate("""
                INSERT INTO interest_pilot.account_input (run_id, acct_id, active_status, current_balance,
                  credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, cycle_credit,
                  cycle_debit, addr_zip, group_id, filler_raw, raw_record)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", accounts, 500, (ps, a) -> {
            ps.setObject(1, runId);
            ps.setString(2, a.acctId());
            ps.setString(3, a.activeStatus());
            ps.setBigDecimal(4, a.currentBalance());
            ps.setBigDecimal(5, a.creditLimit());
            ps.setBigDecimal(6, a.cashCreditLimit());
            ps.setString(7, a.openDate());
            ps.setString(8, a.expirationDate());
            ps.setString(9, a.reissueDate());
            ps.setBigDecimal(10, a.cycleCredit());
            ps.setBigDecimal(11, a.cycleDebit());
            ps.setString(12, a.addrZip());
            ps.setString(13, a.groupId());
            ps.setBytes(14, a.filler());
            ps.setBytes(15, a.rawRecord());
        });
    }

    public void insertCardXrefs(UUID runId, List<CardXref> cards) {
        jdbc.batchUpdate("""
                INSERT INTO interest_pilot.card_xref_input (run_id, card_number, customer_id, acct_id, filler_raw,
                  raw_record) VALUES (?,?,?,?,?,?)""", cards, 500, (ps, c) -> {
            ps.setObject(1, runId);
            ps.setString(2, c.cardNumber());
            ps.setString(3, c.customerId());
            ps.setString(4, c.acctId());
            ps.setBytes(5, c.filler());
            ps.setBytes(6, c.rawRecord());
        });
    }

    public void insertCategoryBalances(UUID runId, List<CategoryBalance> balances) {
        jdbc.batchUpdate("""
                INSERT INTO interest_pilot.category_balance_input (run_id, legacy_key, acct_id, txn_type,
                  category_code, balance, filler_raw, raw_record) VALUES (?,?,?,?,?,?,?,?)""",
                balances, 500, (ps, b) -> {
                    ps.setObject(1, runId);
                    ps.setBytes(2, b.legacyKey());
                    ps.setString(3, b.acctId());
                    ps.setString(4, b.txnType());
                    ps.setString(5, b.categoryCode());
                    ps.setBigDecimal(6, b.balance());
                    ps.setBytes(7, b.filler());
                    ps.setBytes(8, b.rawRecord());
                });
    }

    public void insertDisclosureRates(UUID runId, List<DisclosureRate> rates) {
        jdbc.batchUpdate("""
                INSERT INTO interest_pilot.disclosure_rate_input (run_id, legacy_key, group_id, txn_type,
                  category_code, annual_percent_rate, filler_raw, raw_record) VALUES (?,?,?,?,?,?,?,?)""",
                rates, 500, (ps, d) -> {
                    ps.setObject(1, runId);
                    ps.setBytes(2, d.legacyKey());
                    ps.setString(3, d.groupId());
                    ps.setString(4, d.txnType());
                    ps.setString(5, d.categoryCode());
                    ps.setBigDecimal(6, d.annualRate());
                    ps.setBytes(7, d.filler());
                    ps.setBytes(8, d.rawRecord());
                });
    }

    /** INT-R09: KSDS order is the unsigned byte order of the encoded key (bytea compares as memcmp). */
    public List<CategoryBalance> categoryBalancesInKeyOrder(UUID runId) {
        return jdbc.query("""
                SELECT raw_record FROM interest_pilot.category_balance_input
                WHERE run_id = ? ORDER BY legacy_key""",
                (rs, n) -> CategoryBalance.decode(rs.getBytes(1), n + 1), runId);
    }

    public Optional<Account> findAccount(UUID runId, String acctId) {
        return jdbc.query("SELECT raw_record FROM interest_pilot.account_input WHERE run_id = ? AND acct_id = ?",
                (rs, n) -> Account.decode(rs.getBytes(1), n + 1), runId, acctId).stream().findFirst();
    }

    public List<CardXref> findCardsByAccount(UUID runId, String acctId) {
        return jdbc.query("""
                SELECT raw_record FROM interest_pilot.card_xref_input
                WHERE run_id = ? AND acct_id = ? ORDER BY card_number""",
                (rs, n) -> CardXref.decode(rs.getBytes(1), n + 1), runId, acctId);
    }

    public Optional<DisclosureRate> findRate(UUID runId, String groupId, String txnType, String categoryCode) {
        return jdbc.query("""
                SELECT raw_record FROM interest_pilot.disclosure_rate_input
                WHERE run_id = ? AND group_id = ? AND txn_type = ? AND category_code = ?""",
                (rs, n) -> DisclosureRate.decode(rs.getBytes(1), n + 1), runId, groupId, txnType, categoryCode)
                .stream().findFirst();
    }

    public void insertTransactions(UUID runId, List<InterestTransaction> txs) {
        jdbc.batchUpdate("""
                INSERT INTO interest_pilot.interest_tx (run_id, emit_ordinal, txn_id, txn_type, category_code,
                  source, description, amount, merchant_id, merchant_name, merchant_city, merchant_zip,
                  card_number, orig_ts, proc_ts, filler_raw, raw_record)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", txs, 500, (ps, t) -> {
            ps.setObject(1, runId);
            ps.setLong(2, t.emitOrdinal());
            ps.setString(3, t.txnId());
            ps.setString(4, t.txnType());
            ps.setString(5, t.categoryCode());
            ps.setString(6, t.source());
            ps.setString(7, t.description());
            ps.setBigDecimal(8, t.amount());
            ps.setString(9, t.merchantId());
            ps.setString(10, t.merchantName());
            ps.setString(11, t.merchantCity());
            ps.setString(12, t.merchantZip());
            ps.setString(13, t.cardNumber());
            ps.setString(14, t.origTs());
            ps.setString(15, t.procTs());
            ps.setBytes(16, t.filler());
            ps.setBytes(17, t.toRecord());
        });
    }

    public void insertDelta(UUID runId, AccountDelta d, byte[] sourceRecord) {
        jdbc.update("""
                INSERT INTO interest_pilot.account_delta (run_id, acct_id, old_current_balance,
                  new_current_balance, old_cycle_credit, new_cycle_credit, old_cycle_debit, new_cycle_debit,
                  source_fingerprint) VALUES (?,?,?,?,?,?,?,?,?)""",
                runId, d.acctId(), d.oldCurrentBalance(), d.newCurrentBalance(), d.oldCycleCredit(),
                d.newCycleCredit(), d.oldCycleDebit(), d.newCycleDebit(), sha256(sourceRecord));
    }

    public void insertSysout(UUID runId, List<GroupOutcome.SysoutLine> lines) {
        jdbc.batchUpdate("INSERT INTO interest_pilot.sysout_line (run_id, line_number, text) VALUES (?,?,?)",
                lines, 500, (ps, l) -> {
                    ps.setObject(1, runId);
                    ps.setLong(2, l.lineNumber());
                    ps.setString(3, l.text());
                });
    }

    public void saveCheckpoint(UUID runId, byte[] nextLegacyKey, String lastAcctId, long lastOrdinal) {
        jdbc.update("""
                INSERT INTO interest_pilot.run_checkpoint (run_id, next_legacy_key, last_acct_id, last_ordinal)
                VALUES (?,?,?,?)
                ON CONFLICT (run_id) DO UPDATE SET next_legacy_key = EXCLUDED.next_legacy_key,
                  last_acct_id = EXCLUDED.last_acct_id, last_ordinal = EXCLUDED.last_ordinal""",
                runId, nextLegacyKey, lastAcctId, lastOrdinal);
    }

    public List<byte[]> transactionRecordsInEmitOrder(UUID runId) {
        return jdbc.query("SELECT raw_record FROM interest_pilot.interest_tx WHERE run_id = ? ORDER BY emit_ordinal",
                (rs, n) -> rs.getBytes(1), runId);
    }

    public List<Account> accountsInKeyOrder(UUID runId) {
        return jdbc.query("SELECT raw_record FROM interest_pilot.account_input WHERE run_id = ? ORDER BY acct_id",
                (rs, n) -> Account.decode(rs.getBytes(1), n + 1), runId);
    }

    public Optional<AccountDelta> findDelta(UUID runId, String acctId) {
        return jdbc.query("""
                SELECT acct_id, old_current_balance, new_current_balance, old_cycle_credit, new_cycle_credit,
                  old_cycle_debit, new_cycle_debit FROM interest_pilot.account_delta WHERE run_id = ? AND acct_id = ?""",
                (rs, n) -> new AccountDelta(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7)),
                runId, acctId).stream().findFirst();
    }

    public List<String> sysoutInOrder(UUID runId) {
        return jdbc.queryForList("SELECT text FROM interest_pilot.sysout_line WHERE run_id = ? ORDER BY line_number",
                String.class, runId);
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
