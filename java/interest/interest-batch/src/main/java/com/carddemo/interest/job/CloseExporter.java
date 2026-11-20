package com.carddemo.interest.job;

import com.carddemo.interest.codec.FixedRecordCodec;
import com.carddemo.interest.codec.FixedRecordCodec.*;
import com.carddemo.interest.calc.GroupCalculation;
import com.carddemo.interest.calc.LegacyStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Private artifacts only; not A4 publication. rc.json arrives last, stale success artifacts are removed. */
@Component
public final class CloseExporter {
    public static final String ACCOUNT = "datasets/AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.txt";
    public static final String SYSTRAN = "datasets/AWS.M2.CARDDEMO.SYSTRAN.G0001V00.txt";
    public static final String SYSOUT = "sysout/INTCALC.STEP15.txt";
    private static final List<String> ARTIFACTS = List.of(ACCOUNT, SYSTRAN, SYSOUT, "pilot-failure.json");
    private final CloseStore store;
    public CloseExporter(CloseStore store) { this.store = store; }

    public void export(UUID run, UUID snapshot, Path output, int rc, String category) throws IOException {
        export(run, snapshot, output, rc, category, null);
    }

    public void export(UUID run, UUID snapshot, Path output, int rc, String category, LegacyStatus.Failure failure) throws IOException {
        export(run, snapshot, output, rc, category, failure, null);
    }

    public void export(UUID run, UUID snapshot, Path output, int rc, String category, LegacyStatus.Failure failure, String lastReachedAccount) throws IOException {
        if (output == null) return;
        Path staging = output.resolve(".close-" + UUID.randomUUID());
        Files.createDirectories(staging);
        try {
            if (rc == 0) {
                String state = store.jdbc().queryForObject("SELECT state FROM interest.run WHERE run_id=?", String.class, run);
                if (!"COMPUTED".equals(state)) throw new IllegalStateException("Export requires COMPUTED");
                writeFixed(staging.resolve(SYSTRAN), store.jdbc().query(
                        "SELECT raw_record FROM interest.interest_tx WHERE run_id=? ORDER BY emit_ordinal", (rs, n) -> rs.getBytes(1), run));
                List<byte[]> accounts = new ArrayList<>();
                for (Decoded row : store.rows(snapshot, Layout.ACCOUNT)) {
                    Account account = (Account) row.fields();
                    var patches = store.jdbc().query("SELECT new_current_balance,new_cycle_credit,new_cycle_debit FROM interest.account_delta WHERE run_id=? AND acct_id=?",
                            (rs, n) -> Map.of("currentBalance", rs.getBigDecimal(1), "cycleCredit", rs.getBigDecimal(2), "cycleDebit", rs.getBigDecimal(3)), run, account.acctId());
                    accounts.add(patches.isEmpty() ? row.original() : FixedRecordCodec.encode(row, patches.getFirst()));
                }
                writeFixed(staging.resolve(ACCOUNT), accounts);
            } else {
                Files.writeString(staging.resolve("pilot-failure.json"), "{\"run\":\"" + run + "\",\"category\":\"" + category + "\"}\n");
            }
            Path sysout = staging.resolve(SYSOUT);
            Files.createDirectories(sysout.getParent());
            boolean computed = rc == 0;
            if (!computed && failure == null && lastReachedAccount == null && snapshot != null) {
                computed = Boolean.TRUE.equals(store.jdbc().queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM interest.run WHERE run_id=? AND snapshot_id=? AND state='COMPUTED')",
                        Boolean.class, run, snapshot));
            }
            Files.writeString(sysout, String.join("\n", transcript(snapshot, computed, failure, lastReachedAccount)) + "\n", StandardCharsets.ISO_8859_1);
            Files.deleteIfExists(output.resolve("rc.json"));
            for (String artifact : ARTIFACTS) {
                Files.deleteIfExists(output.resolve(artifact));
                if (Files.exists(staging.resolve(artifact))) {
                    Files.createDirectories(output.resolve(artifact).getParent());
                    Files.move(staging.resolve(artifact), output.resolve(artifact), StandardCopyOption.ATOMIC_MOVE);
                }
            }
            Files.writeString(staging.resolve("rc.json"), "{\"INTCALC\":{\"maxrc\":" + rc + ",\"steps\":{\"STEP15\":" + rc + "}}}\n");
            Files.move(staging.resolve("rc.json"), output.resolve("rc.json"), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            try (var paths = Files.walk(staging)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }

    /** TN-01 / INT-R03 / INT-R10: regenerate restricted DISPLAY from immutable inputs, never ordinary logs. */
    public List<String> transcript(UUID snapshot, boolean success) {
        return transcript(snapshot, success, null);
    }

    public List<String> transcript(UUID snapshot, boolean success, LegacyStatus.Failure failure) {
        return transcript(snapshot, success, failure, null);
    }

    /** TN-01 / INT-R10: target failures cannot manufacture legacy diagnostics from unreached groups. */
    private List<String> transcript(UUID snapshot, boolean success, LegacyStatus.Failure failure, String lastReachedAccount) {
        List<String> lines = new ArrayList<>();
        lines.add("START OF EXECUTION OF PROGRAM CBACT04C");
        if (failure != null && !(failure instanceof GroupCalculation.MissingLookup)) {
            lines.addAll(failure.restrictedPrefix());
            lines.addAll(failure.diagnostics());
            return lines;
        }
        if (snapshot == null || !success && lastReachedAccount == null) {
            if (failure != null) lines.addAll(failure.diagnostics());
            return lines;
        }
        String previous = "";
        Account account = null;
        for (Decoded row : store.rows(snapshot, Layout.TCATBAL)) {
            CategoryBalance balance = (CategoryBalance) row.fields();
            if (!success && previous.equals(lastReachedAccount) && !previous.equals(balance.acctId())) break;
            lines.add(new String(row.original(), StandardCharsets.US_ASCII));
            if (!previous.equals(balance.acctId())) {
                var found = store.account(snapshot, balance.acctId());
                if (found.isEmpty()) {
                    lines.add("ACCOUNT NOT FOUND: " + balance.acctId());
                    lines.addAll(new GroupCalculation.MissingLookup("ACCTFILE").diagnostics()); return lines;
                }
                account = (Account) found.get().fields();
                if (store.card(snapshot, balance.acctId()).isEmpty()) {
                    lines.add("ACCOUNT NOT FOUND: " + balance.acctId());
                    lines.addAll(new GroupCalculation.MissingLookup("XREFFILE").diagnostics()); return lines;
                }
                previous = balance.acctId();
            }
            if (store.rate(snapshot, account.groupId(), balance).isEmpty()) {
                lines.add("DISCLOSURE GROUP RECORD MISSING");
                lines.add("TRY WITH DEFAULT GROUP CODE");
                if (store.rate(snapshot, "DEFAULT   ", balance).isEmpty()) {
                    lines.addAll(new GroupCalculation.MissingLookup("DISCGRP").diagnostics()); return lines;
                }
            }
        }
        if (success) lines.add("END OF EXECUTION OF PROGRAM CBACT04C");
        return lines;
    }

    public static void writeFixed(Path file, List<byte[]> rows) throws IOException {
        Files.createDirectories(file.getParent());
        try (var stream = Files.newOutputStream(file)) {
            for (byte[] row : rows) {
                int end = row.length;
                while (end > 0 && row[end - 1] == ' ') end--;
                stream.write(row, 0, end); stream.write('\n');
            }
        }
    }
}
