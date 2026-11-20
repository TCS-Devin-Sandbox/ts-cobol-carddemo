package com.carddemo.interest.handoff;

import com.carddemo.interest.codec.FixedRecordCodec;
import com.carddemo.interest.codec.FixedRecordCodec.Layout;
import com.carddemo.interest.codec.FixedRecordCodec.Profile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Builds the export packet from immutable staged output only (INT-ARCH A3 Package row): interest_tx in emit-ordinal
 * order, account_delta joined to its sealed account_input preimage. Read-only; any geometry, ordinal, column/raw or
 * preimage disagreement rejects the packet instead of repairing it.
 */
public final class PacketBuilder {

    static final String LIVE_DESTINATION_PREFIX = "AWS.M2.CARDDEMO.";
    static final String SHADOW_DESTINATION_PREFIX = "shadow:";

    private final JdbcTemplate jdbc;

    public PacketBuilder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ExportPacket build(UUID runId) {
        RunRow run = jdbc.query("""
                SELECT r.snapshot_id, r.parm, r.cycle, r.feed, r.mode, r.build_revision, r.rule_revision,
                  r.clock_profile, r.state, s.codec_profile, s.state
                FROM interest.run r JOIN interest.snapshot s USING (snapshot_id) WHERE r.run_id = ?""",
                (rs, n) -> new RunRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getString(10), rs.getString(11)), runId).stream().findFirst()
                .orElseThrow(() -> new PacketRejected("RUN_MISSING"));
        if (!run.state.equals("COMPUTED") && !run.state.equals("HELD")) {
            throw new PacketRejected("RUN_NOT_COMPUTED");
        }
        if (!run.snapshotState.equals("SEALED")) {
            throw new PacketRejected("SNAPSHOT_NOT_SEALED");
        }
        Profile profile;
        try {
            profile = Profile.valueOf(run.codecProfile.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new PacketRejected("CODEC_PROFILE");
        }
        List<ExportPacket.PayloadRecord> records = records(runId, profile);
        List<ExportPacket.GuardedDelta> deltas = deltas(runId, run.snapshotId, profile);
        byte[] payload = new byte[records.size() * ExportPacket.LRECL];
        for (int i = 0; i < records.size(); i++) {
            System.arraycopy(records.get(i).raw(), 0, payload, i * ExportPacket.LRECL, ExportPacket.LRECL);
        }
        byte[] digest = Digests.sha256(payload);
        String destination = (run.mode.equals("shadow") ? SHADOW_DESTINATION_PREFIX : "")
                + LIVE_DESTINATION_PREFIX + run.feed;
        return new ExportPacket(runId, run.mode, destination, run.cycle, run.feed, records, deltas, digest,
                manifest(runId, run, destination, records, deltas, digest));
    }

    private List<ExportPacket.PayloadRecord> records(UUID runId, Profile profile) {
        List<ExportPacket.PayloadRecord> out = new ArrayList<>();
        jdbc.query("""
                SELECT emit_ordinal, txn_id, amount, filler_raw, raw_record, txn_type, category_code, source, description,
                  merchant_id, merchant_name, merchant_city, merchant_zip, card_number, orig_ts, proc_ts
                FROM interest.interest_tx
                WHERE run_id = ? ORDER BY emit_ordinal""", rs -> {
            long ordinal = rs.getLong(1);
            byte[] raw = rs.getBytes(5);
            if (ordinal != out.size() + 1) {
                throw new PacketRejected("ORDINAL_GAP");
            }
            if (raw.length != ExportPacket.LRECL) {
                throw new PacketRejected("RECORD_GEOMETRY");
            }
            FixedRecordCodec.InterestTransaction t;
            try {
                t = (FixedRecordCodec.InterestTransaction) FixedRecordCodec.decode(Layout.TRAN, profile, raw).fields();
            } catch (FixedRecordCodec.Rejection e) {
                throw new PacketRejected("RECORD_CODEC_" + e.row() + "_" + e.reason());
            }
            List<String> columns = List.of(rs.getString(2), rs.getString(6), rs.getString(7), rs.getString(8),
                    rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13),
                    rs.getString(14), rs.getString(15), rs.getString(16));
            List<String> decoded = List.of(t.txnId(), t.txnType(), t.categoryCode(), t.source(), t.description(),
                    t.merchantId(), t.merchantName(), t.merchantCity(), t.merchantZip(), t.cardNumber(), t.origTs(),
                    t.procTs());
            if (!decoded.equals(columns) || t.amount().compareTo(rs.getBigDecimal(3)) != 0
                    || !Arrays.equals(t.filler().array(), rs.getBytes(4))) {
                throw new PacketRejected("RECORD_COLUMN_MISMATCH");
            }
            out.add(new ExportPacket.PayloadRecord(ordinal, t.txnId(), raw));
        }, runId);
        return out;
    }

    private List<ExportPacket.GuardedDelta> deltas(UUID runId, UUID snapshotId, Profile profile) {
        List<ExportPacket.GuardedDelta> out = new ArrayList<>();
        jdbc.query("""
                SELECT d.acct_id, d.old_current_balance, d.new_current_balance, d.old_cycle_credit, d.new_cycle_credit,
                  d.old_cycle_debit, d.new_cycle_debit, d.source_fingerprint, a.raw_record
                FROM interest.account_delta d
                LEFT JOIN interest.account_input a ON a.snapshot_id = ? AND a.acct_id = d.acct_id
                WHERE d.run_id = ? ORDER BY d.acct_id""", rs -> {
            byte[] fingerprint = rs.getBytes(8);
            byte[] preimage = rs.getBytes(9);
            if (preimage == null || !Arrays.equals(Digests.sha256(preimage), fingerprint)) {
                throw new PacketRejected("DELTA_PREIMAGE");
            }
            FixedRecordCodec.Decoded decoded;
            try {
                decoded = FixedRecordCodec.decode(Layout.ACCOUNT, profile, preimage);
            } catch (FixedRecordCodec.Rejection e) {
                throw new PacketRejected("DELTA_CODEC_" + e.row() + "_" + e.reason());
            }
            FixedRecordCodec.Account account = (FixedRecordCodec.Account) decoded.fields();
            BigDecimal oldBalance = rs.getBigDecimal(2), oldCredit = rs.getBigDecimal(4), oldDebit = rs.getBigDecimal(6);
            if (!account.acctId().equals(rs.getString(1)) || account.currentBalance().compareTo(oldBalance) != 0
                    || account.cycleCredit().compareTo(oldCredit) != 0 || account.cycleDebit().compareTo(oldDebit) != 0) {
                throw new PacketRejected("DELTA_OLD_VALUES");
            }
            BigDecimal newBalance = rs.getBigDecimal(3), newCredit = rs.getBigDecimal(5), newDebit = rs.getBigDecimal(7);
            byte[] postImage;
            try {
                postImage = FixedRecordCodec.encode(decoded,
                        Map.of("currentBalance", newBalance, "cycleCredit", newCredit, "cycleDebit", newDebit));
            } catch (FixedRecordCodec.Rejection e) {
                throw new PacketRejected("DELTA_CODEC_" + e.row() + "_" + e.reason());
            }
            out.add(new ExportPacket.GuardedDelta(account.acctId(), oldBalance, newBalance, oldCredit, newCredit,
                    oldDebit, newDebit, fingerprint, postImage));
        }, snapshotId, runId);
        return out;
    }

    /** Canonical manifest: fixed key order, no whitespace, decimals at scale 2, hex digests (deterministic bytes). */
    private static String manifest(UUID runId, RunRow run, String destination, List<ExportPacket.PayloadRecord> records,
            List<ExportPacket.GuardedDelta> deltas, byte[] digest) {
        HexFormat hex = HexFormat.of();
        StringBuilder m = new StringBuilder("{\"format\":\"INT-05/1\"");
        field(m, "run_id", runId.toString());
        field(m, "snapshot_id", run.snapshotId.toString());
        field(m, "mode", run.mode);
        field(m, "destination", destination);
        field(m, "cycle", run.cycle);
        field(m, "feed", run.feed);
        field(m, "parm", run.parm);
        field(m, "codec_profile", run.codecProfile);
        field(m, "build_revision", run.buildRevision);
        field(m, "rule_revision", run.ruleRevision);
        field(m, "clock_profile", run.clockProfile);
        m.append(",\"recfm\":\"F\",\"lrecl\":").append(ExportPacket.LRECL).append(",\"record_count\":").append(records.size());
        field(m, "payload_sha256", hex.formatHex(digest));
        m.append(",\"txn_ids\":[");
        for (int i = 0; i < records.size(); i++) {
            m.append(i == 0 ? "" : ",").append('"').append(records.get(i).txnId()).append('"');
        }
        m.append("],\"deltas\":[");
        for (int i = 0; i < deltas.size(); i++) {
            ExportPacket.GuardedDelta d = deltas.get(i);
            m.append(i == 0 ? "{" : ",{").append("\"acct_id\":\"").append(d.acctId()).append('"');
            field(m, "preimage_sha256", hex.formatHex(d.preimageFingerprint()));
            field(m, "postimage_sha256", hex.formatHex(d.postImageFingerprint()));
            field(m, "old_current_balance", d.oldCurrentBalance().toPlainString());
            field(m, "new_current_balance", d.newCurrentBalance().toPlainString());
            field(m, "old_cycle_credit", d.oldCycleCredit().toPlainString());
            field(m, "new_cycle_credit", d.newCycleCredit().toPlainString());
            field(m, "old_cycle_debit", d.oldCycleDebit().toPlainString());
            field(m, "new_cycle_debit", d.newCycleDebit().toPlainString());
            m.append('}');
        }
        return m.append("]}").toString();
    }

    private static void field(StringBuilder m, String key, String value) {
        m.append(",\"").append(key).append("\":\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') {
                m.append('\\').append(c);
            } else if (c < 0x20 || c > 0x7e) {
                m.append("\\u%04x".formatted((int) c));
            } else {
                m.append(c);
            }
        }
        m.append('"');
    }

    private record RunRow(UUID snapshotId, String parm, String cycle, String feed, String mode, String buildRevision,
            String ruleRevision, String clockProfile, String state, String codecProfile, String snapshotState) {}

    /** Target packaging failure (standards S12 last row): no legacy file status, no ready packet. */
    public static final class PacketRejected extends RuntimeException {
        private final String reason;

        PacketRejected(String reason) {
            super("packet rejected: " + reason);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }
}
