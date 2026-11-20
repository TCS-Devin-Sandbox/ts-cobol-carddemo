package com.carddemo.interest.intake;

import com.carddemo.interest.codec.FixedRecordCodec;
import com.carddemo.interest.codec.FixedRecordCodec.Bytes;
import com.carddemo.interest.codec.FixedRecordCodec.Decoded;
import com.carddemo.interest.codec.FixedRecordCodec.Layout;
import com.carddemo.interest.codec.FixedRecordCodec.Profile;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** INT-ARCH A1/A2: one immutable, validated LAB_ASCII capture; no lookup referential constraints. */
@Component
public final class SnapshotIntake {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public SnapshotIntake(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public record Result(UUID snapshotId, boolean sealed, String reason) {}
    public record FirstReturn(String cardNumber, String evidence) {}
    private record Input(Layout layout, String stem, String table, String columns) {}
    private static final List<Input> INPUTS = List.of(
            new Input(Layout.ACCOUNT, "ACCTDATA", "account_input", "acct_id,active_status,current_balance,credit_limit,cash_credit_limit,open_date,expiration_date,reissue_date,cycle_credit,cycle_debit,addr_zip,group_id,filler_raw"),
            new Input(Layout.XREF, "CARDXREF", "card_xref_input", "card_number,customer_id,acct_id,filler_raw"),
            new Input(Layout.TCATBAL, "TCATBALF", "category_balance_input", "acct_id,txn_type,category_code,balance,filler_raw"),
            new Input(Layout.DISCGRP, "DISCGRP", "disclosure_rate_input", "group_id,txn_type,category_code,annual_percent_rate,filler_raw"));

    public Result load(Path directory) {
        return load(directory, Map.of());
    }

    /** INT-H03: UTF-8 TSV rows contain account, externally observed first card, and evidence reference. */
    public Result load(Path directory, Path evidenceFile) {
        try {
            Map<String, FirstReturn> firstCards = new LinkedHashMap<>();
            List<String> lines = Files.readAllLines(evidenceFile, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String[] fields = lines.get(i).split("\t", -1);
                if (fields.length != 3 || !fields[0].matches("[0-9]{11}") || !fields[1].matches("[0-9]{16}")
                        || fields[2].isBlank() || firstCards.putIfAbsent(fields[0], new FirstReturn(fields[1], fields[2])) != null)
                    throw new Rejected("XREF/X03 row=" + (i + 1) + " offset=25 reason=INT-H03_AIX_EVIDENCE_INVALID");
            }
            return load(directory, Map.copyOf(firstCards));
        } catch (IOException | Rejected e) {
            UUID id = UUID.randomUUID();
            String reason = e instanceof Rejected ? e.getMessage() : "XREF/X03 row=0 offset=25 reason=INT-H03_AIX_EVIDENCE_UNREADABLE";
            jdbc.update("INSERT INTO interest.snapshot VALUES (?, jsonb_build_object('reason',?::text), 'LAB_ASCII', 'HELD', CURRENT_TIMESTAMP)", id, reason);
            return new Result(id, false, reason);
        }
    }

    /** INT-H03: evidence maps an account to the externally observed first-return card, never MIN(card). */
    public Result load(Path directory, Map<String, FirstReturn> firstCards) {
        UUID id = UUID.randomUUID();
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO interest.snapshot VALUES (?, '{}'::jsonb, 'LAB_ASCII', 'LOADING', CURRENT_TIMESTAMP)", id);
                List<String> manifests = new ArrayList<>();
                for (Input input : INPUTS) {
                    byte[] source;
                    try {
                        source = Files.readAllBytes(directory.resolve("AWS.M2.CARDDEMO." + input.stem + ".PS.txt"));
                    } catch (IOException e) {
                        throw new Rejected(input.layout + "/RECORD row=0 offset=0 reason=INPUT_UNREADABLE");
                    }
                    List<Decoded> rows = decodeLines(input.layout, source);
                    if (input.layout == Layout.XREF) {
                        Map<String, List<Decoded>> byAccount = rows.stream().collect(Collectors.groupingBy(
                                r -> ((FixedRecordCodec.CardXref) r.fields()).acctId()));
                        for (var group : byAccount.entrySet()) {
                            FirstReturn proof = firstCards.get(group.getKey());
                            if (group.getValue().size() > 1 && (proof == null || proof.evidence() == null || proof.evidence().isBlank()
                                    || group.getValue().stream().noneMatch(r -> ((FixedRecordCodec.CardXref) r.fields()).cardNumber().equals(proof.cardNumber())))) {
                                throw new Rejected("XREF/X03 row=0 offset=25 reason=INT-H03_AIX_EVIDENCE_REQUIRED");
                            }
                        }
                    }
                    Map<String, BigDecimal> totals = new LinkedHashMap<>();
                    input.layout.fields().stream().filter(field -> field.kind() == 'z')
                            .forEach(field -> totals.put(field.name(), BigDecimal.ZERO.setScale(2)));
                    for (Decoded row : rows) {
                        List<Object> args = new ArrayList<>();
                        args.add(id);
                        row.fields().values().forEach(v -> args.add(v instanceof Bytes b ? b.array() : v));
                        args.add(row.original());
                        String extra = "";
                        if (input.layout == Layout.TCATBAL || input.layout == Layout.DISCGRP) {
                            extra = ",legacy_key";
                            args.add(row.legacyKey());
                        } else if (input.layout == Layout.XREF) {
                            var card = (FixedRecordCodec.CardXref) row.fields();
                            long count = rows.stream().filter(r -> ((FixedRecordCodec.CardXref) r.fields()).acctId().equals(card.acctId())).count();
                            extra = ",aix_first,aix_evidence";
                            args.add(count == 1 || card.cardNumber().equals(firstCards.get(card.acctId()).cardNumber()));
                            args.add(count == 1 ? "single-card" : firstCards.get(card.acctId()).evidence());
                        }
                        jdbc.update("INSERT INTO interest." + input.table + " (snapshot_id," + input.columns
                                + ",raw_record" + extra + ") VALUES (" + String.join(",", java.util.Collections.nCopies(args.size(), "?")) + ")", args.toArray());
                        for (int field = 0; field < input.layout.fields().size(); field++) {
                            Object value = row.fields().values().get(field);
                            if (value instanceof BigDecimal number) totals.merge(input.layout.fields().get(field).name(), number, BigDecimal::add);
                        }
                    }
                    String decodedTotals = totals.entrySet().stream().map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue().toPlainString() + "\"")
                            .collect(Collectors.joining(","));
                    manifests.add("\"" + input.stem + "\":{\"count\":" + rows.size() + ",\"sha256\":\""
                            + HexFormat.of().formatHex(sha256(source)) + "\",\"record_sha256\":\"" + recordDigest(rows)
                            + "\",\"decoded_totals\":{" + decodedTotals + "}}");
                }
                jdbc.update("UPDATE interest.snapshot SET manifest=?::jsonb,state='SEALED' WHERE snapshot_id=? AND state='LOADING'",
                        "{" + String.join(",", manifests) + "}", id);
            });
            return new Result(id, true, "");
        } catch (Rejected e) {
            jdbc.update("INSERT INTO interest.snapshot VALUES (?, jsonb_build_object('reason',?::text), 'LAB_ASCII', 'HELD', CURRENT_TIMESTAMP)", id, e.getMessage());
            return new Result(id, false, e.getMessage());
        } catch (RuntimeException e) {
            String reason = "TARGET_INTAKE_INFRASTRUCTURE";
            jdbc.update("INSERT INTO interest.snapshot VALUES (?, jsonb_build_object('reason',?::text), 'LAB_ASCII', 'HELD', CURRENT_TIMESTAMP)", id, reason);
            return new Result(id, false, reason);
        }
    }

    /** Harness REPRO profile: LF-delimited payloads, omitted trailing spaces padded to the fixed LRECL. */
    public static List<Decoded> decodeLines(Layout layout, byte[] source) {
        List<Decoded> rows = new ArrayList<>();
        HashSet<Bytes> keys = new HashSet<>();
        int start = 0;
        while (start < source.length) {
            int end = start;
            while (end < source.length && source[end] != '\n') end++;
            if (end - start > layout.length()) throw new Rejected(layout + "/RECORD row=" + (rows.size() + 1) + " offset=" + layout.length() + " reason=GEOMETRY");
            byte[] raw = new byte[layout.length()];
            Arrays.fill(raw, (byte) ' ');
            System.arraycopy(source, start, raw, 0, end - start);
            Decoded row;
            try {
                row = FixedRecordCodec.decode(layout, Profile.LAB_ASCII, raw);
            } catch (FixedRecordCodec.Rejection e) {
                throw new Rejected(layout + "/" + e.row() + " row=" + (rows.size() + 1) + " offset=" + e.offset() + " reason=" + e.reason());
            }
            if (!keys.add(new Bytes(row.legacyKey()))) throw new Rejected(layout + "/KEY row=" + (rows.size() + 1) + " offset=0 reason=DUPLICATE_PRIMARY_KEY");
            rows.add(row);
            start = end + 1;
        }
        return List.copyOf(rows);
    }

    public static byte[] sha256(byte[] source) {
        try { return MessageDigest.getInstance("SHA-256").digest(source); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static String recordDigest(List<Decoded> rows) {
        var bytes = new java.io.ByteArrayOutputStream();
        rows.stream().sorted((a, b) -> FixedRecordCodec.compareKeys(a.legacyKey(), b.legacyKey()))
                .forEach(row -> bytes.writeBytes(row.original()));
        return HexFormat.of().formatHex(sha256(bytes.toByteArray()));
    }

    public static final class Rejected extends RuntimeException {
        public Rejected(String reason) { super(reason); }
    }
}
