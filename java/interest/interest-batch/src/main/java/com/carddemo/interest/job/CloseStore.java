package com.carddemo.interest.job;

import com.carddemo.interest.codec.FixedRecordCodec;
import com.carddemo.interest.codec.FixedRecordCodec.*;
import com.carddemo.interest.intake.SnapshotIntake;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CloseStore {
    private final JdbcTemplate jdbc;
    public CloseStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public JdbcTemplate jdbc() { return jdbc; }

    public List<Decoded> rows(UUID snapshot, Layout layout) {
        String table = switch (layout) {
            case ACCOUNT -> "account_input"; case XREF -> "card_xref_input";
            case TCATBAL -> "category_balance_input"; case DISCGRP -> "disclosure_rate_input";
            default -> throw new IllegalArgumentException("input layout required");
        };
        String key = switch (layout) { case ACCOUNT -> "acct_id"; case XREF -> "card_number"; default -> "legacy_key"; };
        return jdbc.query("SELECT raw_record," + key + " FROM interest." + table + " WHERE snapshot_id=? ORDER BY " + key,
                (rs, n) -> {
                    Decoded row = FixedRecordCodec.decode(layout, Profile.LAB_ASCII, rs.getBytes(1));
                    byte[] stored = layout == Layout.ACCOUNT || layout == Layout.XREF
                            ? rs.getString(2).getBytes(java.nio.charset.StandardCharsets.US_ASCII) : rs.getBytes(2);
                    if (FixedRecordCodec.compareKeys(row.legacyKey(), stored) != 0)
                        throw new IllegalArgumentException("Snapshot encoded key mismatch");
                    return row;
                }, snapshot);
    }

    public Optional<Decoded> account(UUID snapshot, String account) {
        return find(snapshot, Layout.ACCOUNT, "account_input", "acct_id=?", account);
    }

    /** INT-H03: intake's evidenced AIX-first row, not a sorted arbitrary card. */
    public Optional<Decoded> card(UUID snapshot, String account) {
        return find(snapshot, Layout.XREF, "card_xref_input", "acct_id=? AND aix_first", account);
    }

    public Optional<Decoded> rate(UUID snapshot, String group, CategoryBalance balance) {
        return find(snapshot, Layout.DISCGRP, "disclosure_rate_input", "group_id=? AND txn_type=? AND category_code=?",
                group, balance.txnType(), balance.categoryCode());
    }

    private Optional<Decoded> find(UUID snapshot, Layout layout, String table, String predicate, Object... keys) {
        List<Object> args = new ArrayList<>(); args.add(snapshot); args.addAll(List.of(keys));
        return jdbc.query("SELECT raw_record FROM interest." + table + " WHERE snapshot_id=? AND " + predicate,
                (rs, n) -> FixedRecordCodec.decode(layout, Profile.LAB_ASCII, rs.getBytes(1)), args.toArray()).stream().findFirst();
    }

    public void transaction(UUID run, long ordinal, InterestTransaction row) {
        List<Object> args = new ArrayList<>(); args.add(run); args.add(ordinal);
        row.values().forEach(v -> args.add(v instanceof Bytes b ? b.array() : v));
        args.add(FixedRecordCodec.encode(Layout.TRAN, Profile.LAB_ASCII, row));
        jdbc.update("""
                INSERT INTO interest.interest_tx (run_id,emit_ordinal,txn_id,txn_type,category_code,source,
                description,amount,merchant_id,merchant_name,merchant_city,merchant_zip,card_number,orig_ts,proc_ts,filler_raw,raw_record)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", args.toArray());
    }

    /** INT-R06 / INT-R07 / INT-H09: guarded preimage delta; inputs and category balances never change. */
    public void delta(UUID run, Decoded original, BigDecimal closing) {
        Account a = (Account) original.fields();
        jdbc.update("""
                INSERT INTO interest.account_delta VALUES (?,?,?,?,?,?,?,?,?)""", run, a.acctId(), a.currentBalance(), closing,
                a.cycleCredit(), new BigDecimal("0.00"), a.cycleDebit(), new BigDecimal("0.00"), SnapshotIntake.sha256(original.original()));
    }

    public void checkpoint(UUID run, byte[] next, String account, long ordinal) {
        jdbc.update("""
                INSERT INTO interest.run_checkpoint VALUES (?,?,?,?) ON CONFLICT (run_id) DO UPDATE
                SET next_legacy_key=EXCLUDED.next_legacy_key,last_acct_id=EXCLUDED.last_acct_id,last_ordinal=EXCLUDED.last_ordinal""",
                run, next, account, ordinal);
    }

    public record Checkpoint(boolean present, Bytes next, long ordinal) {}
    public Checkpoint checkpoint(UUID run) {
        return jdbc.query("SELECT next_legacy_key,last_ordinal FROM interest.run_checkpoint WHERE run_id=?", (rs, n) -> {
            byte[] next = rs.getBytes(1);
            return new Checkpoint(true, next == null ? null : new Bytes(next), rs.getLong(2));
        }, run).stream().findFirst().orElse(new Checkpoint(false, null, 0));
    }
}
