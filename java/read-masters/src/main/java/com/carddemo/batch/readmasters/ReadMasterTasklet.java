package com.carddemo.batch.readmasters;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.carddemo.batch.common.codec.RecordLayout;
import com.carddemo.batch.common.legacy.CobDatFt;
import com.carddemo.batch.common.legacy.CodatecnRecord;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

final class ReadMasterTasklet implements Tasklet {
    private static final String PREFIX = "AWS.M2.CARDDEMO.";
    private static final FixedRecordCodec PACKED = new FixedRecordCodec(new RecordLayout(List.of(
            new RecordLayout.Field("value", RecordLayout.Kind.COMP3, 7, 12, 2, true))));

    private final ReadMaster master;
    private final ReadMasterStore store;

    ReadMasterTasklet(ReadMaster master, ReadMasterStore store) {
        this.master = master;
        this.store = store;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        var step = contribution.getStepExecution();
        var parameters = step.getJobParameters();
        Path out = Path.of(parameters.getString("out"));
        Files.createDirectories(out.resolve("datasets"));
        var sysout = new StringBuilder("START OF EXECUTION OF PROGRAM " + master.program + "\n");
        int rc = 0;
        if ("true".equals(parameters.getString("missing." + master.job))) {
            abend(sysout, "ERROR OPENING " + master.ddname, "35");
            rc = 12;
        } else {
            var codec = new FixedRecordCodec(master.dataset.layout.load());
            var exports = new AccountExports(out);
            for (var values : store.records(master)) {
                String record = new String(codec.encode(values), StandardCharsets.ISO_8859_1);
                if (master == ReadMaster.ACCOUNT) exports.accept(record, values, sysout);
                else {
                    sysout.append(record).append('\n');
                    if (master != ReadMaster.CARD) sysout.append(record).append('\n');
                }
            }
            if (master == ReadMaster.ACCOUNT) exports.write();
            sysout.append("END OF EXECUTION OF PROGRAM ").append(master.program).append('\n');
            if (master == ReadMaster.ACCOUNT) {
                sysout.append("\nlibcob: warning: implicit CLOSE of VBRC-FILE ('VBRCFILE')\n")
                        .append("libcob: warning: implicit CLOSE of ARRY-FILE ('ARRYFILE')\n")
                        .append("libcob: warning: implicit CLOSE of OUT-FILE ('OUTFILE')\n");
            }
        }
        BatchArtifacts.writeSysout(out, master.job, "STEP05", sysout.toString());
        step.getExecutionContext().putInt("returnCode", rc);
        return RepeatStatus.FINISHED;
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
