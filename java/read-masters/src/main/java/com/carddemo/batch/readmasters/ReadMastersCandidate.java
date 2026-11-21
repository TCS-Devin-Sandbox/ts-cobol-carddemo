package com.carddemo.batch.readmasters;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.codec.CardDemoLayout;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.carddemo.batch.common.codec.RecordLayout;
import com.carddemo.batch.common.legacy.CobDatFt;
import com.carddemo.batch.common.legacy.CodatecnRecord;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ReadMastersCandidate {
    private static final String PREFIX = "AWS.M2.CARDDEMO.";
    private static final FixedRecordCodec PACKED = new FixedRecordCodec(new RecordLayout(List.of(
            new RecordLayout.Field("value", RecordLayout.Kind.COMP3, 7, 12, 2, true))));

    private ReadMastersCandidate() { }

    public static void main(String[] args) throws Exception {
        var options = new HashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length || !args[i].startsWith("--")
                    || options.putIfAbsent(args[i], args[i + 1]) != null) {
                throw new IllegalArgumentException("Expected one value per option");
            }
        }
        if (!"read-masters".equals(options.get("--chain"))) {
            throw new IllegalArgumentException("The candidate only supports read-masters");
        }
        run(Path.of(required(options, "--input")), Path.of(required(options, "--out")));
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    public static void run(Path input, Path out) throws Exception {
        Files.createDirectories(out.resolve("datasets"));
        var codes = new TreeMap<String, Map<String, Integer>>();
        String[] jobs = {"READACCT", "READCARD", "READXREF", "READCUST"};
        String[] programs = {"CBACT01C", "CBACT02C", "CBACT03C", "CBCUS01C"};
        String[] datasets = {"ACCTDATA", "CARDDATA", "CARDXREF", "CUSTDATA"};
        String[] ddnames = {"ACCTFILE", "CARDFILE", "XREFFILE", "CUSTFILE"};
        CardDemoLayout[] layouts = {CardDemoLayout.CVACT01Y, CardDemoLayout.CVACT02Y,
                CardDemoLayout.CVACT03Y, CardDemoLayout.CVCUS01Y};
        for (int i = 0; i < jobs.length; i++) {
            var sysout = new StringBuilder("START OF EXECUTION OF PROGRAM " + programs[i] + "\n");
            Path file = input.resolve(PREFIX + datasets[i] + ".PS.txt");
            int rc = 0;
            if (Files.notExists(file)) {
                abend(sysout, "ERROR OPENING " + ddnames[i], "35");
                rc = 12;
            } else {
                var layout = layouts[i].load();
                var codec = new FixedRecordCodec(layout);
                var records = new TreeMap<String, String>();
                for (String record : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
                    record = (record + " ".repeat(layout.length())).substring(0, layout.length());
                    records.putIfAbsent(record.substring(0, layout.fields().getFirst().length()), record);
                }
                var exports = new AccountExports(out);
                for (String record : records.values()) {
                    if (i == 0) exports.accept(record, codec.decode(record.getBytes(StandardCharsets.ISO_8859_1)), sysout);
                    else {
                        sysout.append(record).append('\n');
                        if (i >= 2) sysout.append(record).append('\n');
                    }
                }
                if (i == 0) exports.write();
                sysout.append("END OF EXECUTION OF PROGRAM ").append(programs[i]).append('\n');
                if (i == 0) {
                    sysout.append("\nlibcob: warning: implicit CLOSE of VBRC-FILE ('VBRCFILE')\n")
                            .append("libcob: warning: implicit CLOSE of ARRY-FILE ('ARRYFILE')\n")
                            .append("libcob: warning: implicit CLOSE of OUT-FILE ('OUTFILE')\n");
                }
            }
            BatchArtifacts.writeSysout(out, jobs[i], "STEP05", sysout.toString());
            codes.put(jobs[i], i == 0 ? Map.of("PREDEL", 0, "STEP05", rc) : Map.of("STEP05", rc));
        }
        BatchArtifacts.writeReturnCodes(out, codes);
    }

    private static void abend(StringBuilder text, String message, String status) {
        text.append(message).append("\nFILE STATUS IS: NNNN00").append(status)
                .append("\nABENDING PROGRAM\nCEE3ABD: USER ABEND U+000000999\n");
    }

    private static String packed(String value) {
        return new String(PACKED.encode(Map.of("value", new BigDecimal(value))), StandardCharsets.ISO_8859_1);
    }

    private static final class AccountExports {
        private final Path out;
        private final StringBuilder fixed = new StringBuilder();
        private final StringBuilder array = new StringBuilder();
        private final StringBuilder variable = new StringBuilder();
        private String debit = "\0".repeat(7);

        private AccountExports(Path out) { this.out = out; }

        private void accept(String raw, Map<String, Object> values, StringBuilder sysout) {
            String[] names = {"ACCT-ID", "ACCT-ACTIVE-STATUS", "ACCT-CURR-BAL", "ACCT-CREDIT-LIMIT",
                    "ACCT-CASH-CREDIT-LIMIT", "ACCT-OPEN-DATE", "ACCT-EXPIRAION-DATE", "ACCT-REISSUE-DATE",
                    "ACCT-CURR-CYC-CREDIT", "ACCT-CURR-CYC-DEBIT", "ACCT-GROUP-ID"};
            for (String name : names) {
                Object value = values.get(name);
                String display = value.toString();
                if (value instanceof BigDecimal number) {
                    int width = name.equals("ACCT-ID") ? 11 : 12;
                    String digits = number.unscaledValue().abs().toString();
                    display = "0".repeat(width - digits.length()) + digits;
                    if (width == 12) display += number.signum() < 0 ? "-" : "+";
                }
                sysout.append(String.format(java.util.Locale.ROOT, "%-24s:", name)).append(display).append('\n');
            }
            sysout.append("-------------------------------------------------\n");
            String id = raw.substring(0, 11);
            String balance = raw.substring(12, 24);
            String limit = raw.substring(24, 36);
            String date = values.get("ACCT-REISSUE-DATE").toString();
            String converted = CobDatFt.call(CodatecnRecord.request('2', date, '2')).outputDate().substring(0, 10);
            if (((BigDecimal) values.get("ACCT-CURR-CYC-DEBIT")).signum() == 0) debit = packed("2525.00");
            hexLine(fixed, raw.substring(0, 68) + converted + raw.substring(78, 90) + debit
                    + values.get("ACCT-GROUP-ID"));
            hexLine(array, id + balance + packed("1005.00") + balance + packed("1525.00")
                    + "00000010250}" + packed("-2500.00")
                    + ("000000000000" + packed("0.00")).repeat(2) + "    ");
            String vb1 = raw.substring(0, 12);
            String vb2 = id + balance + limit + date.substring(0, 4);
            variable.append(vb1).append('\n').append(vb2).append('\n');
            sysout.append("VBRC-REC1:").append(vb1).append("\nVBRC-REC2:").append(vb2)
                    .append('\n').append(raw).append('\n');
        }

        private static void hexLine(StringBuilder target, String record) {
            target.append(HexFormat.of().formatHex(record.getBytes(StandardCharsets.ISO_8859_1))).append('\n');
        }

        private void write() throws Exception {
            write("PSCOMP", fixed);
            write("ARRYPS", array);
            write("VBPS", variable);
        }

        private void write(String suffix, StringBuilder records) throws Exception {
            Files.writeString(out.resolve("datasets").resolve(PREFIX + "ACCTDATA." + suffix + ".txt"),
                    records, StandardCharsets.ISO_8859_1);
        }
    }
}
