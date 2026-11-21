package com.carddemo.batch.fees;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.carddemo.batch.common.codec.CardDemoLayout;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.carddemo.batch.common.codec.RecordLayout;
import com.carddemo.batch.common.codec.RecordLayout.Field;
import com.carddemo.batch.common.codec.RecordLayout.Kind;

public final class FeeDriver {
    private final FixedRecordCodec accounts = new FixedRecordCodec(CardDemoLayout.CVACT01Y.load());
    private final FeeCalculator calculator;
    private final FixedRecordCodec lines = new FixedRecordCodec(new RecordLayout(List.of(
            text("FL-ACCT-ID", 11), text("FILLER@11", 1), text("FL-GROUP-ID", 10),
            text("FILLER@22", 1), text("FL-CYCLE-BAL", 14), text("FILLER@37", 1),
            text("FL-FEE-AMT", 11), text("FILLER@49", 1), text("FL-RC", 2), text("FILLER@52", 28))));

    public FeeDriver() throws IOException {
        this(System.out);
    }

    public FeeDriver(PrintStream sysprint) throws IOException {
        calculator = new FeeCalculator(sysprint);
    }

    public FeeRequest request(byte[] account) {
        var fields = accounts.decode(account);
        BigDecimal balance = ((BigDecimal) fields.get("ACCT-CURR-CYC-CREDIT"))
                .subtract((BigDecimal) fields.get("ACCT-CURR-CYC-DEBIT"))
                .setScale(2);
        // COBOL COMPUTE into S9(10)V99 without ON SIZE ERROR keeps low-order digits.
        balance = balance.remainder(new BigDecimal("10000000000.00"));
        String id = String.format(Locale.ROOT, "%011d", ((BigDecimal) fields.get("ACCT-ID")).toBigIntegerExact());
        return new FeeRequest(id, (String) fields.get("ACCT-GROUP-ID"), balance,
                balance.signum() > 0 ? 10 : 0, 30, new BigDecimal("0.00"), new BigDecimal("0.00"), 0);
    }

    public byte[] output(FeeRequest response) {
        return lines.encode(Map.of("FL-ACCT-ID", response.FEE_ACCT_ID(), "FILLER@11", " ",
                "FL-GROUP-ID", response.FEE_GROUP_ID(), "FILLER@22", " ",
                "FL-CYCLE-BAL", edited(response.FEE_CYCLE_BAL(), 14), "FILLER@37", " ",
                "FL-FEE-AMT", edited(response.FEE_AMT(), 11), "FILLER@49", " ",
                "FL-RC", String.format(Locale.ROOT, "%02d", response.FEE_RC()), "FILLER@52", ""));
    }

    public void run(InputStream input, OutputStream output) throws IOException {
        byte[] account;
        while ((account = input.readNBytes(300)).length != 0) {
            if (account.length != 300) throw new IOException("Truncated ACCTFILE record");
            output.write(output(calculator.calculate(request(account))));
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: FeeDriver ACCTFILE FEEOUT (fixed records)");
        try (var input = Files.newInputStream(Path.of(args[0]));
             var output = Files.newOutputStream(Path.of(args[1]))) {
            new FeeDriver(System.out).run(input, output);
        }
    }

    private static Field text(String name, int length) {
        return new Field(name, Kind.TEXT, length, 0, 0, false);
    }

    private static String edited(BigDecimal value, int width) {
        String number = value.setScale(2).toPlainString();
        return " ".repeat(width - number.length()) + number;
    }
}
