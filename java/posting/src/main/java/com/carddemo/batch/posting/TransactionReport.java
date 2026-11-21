package com.carddemo.batch.posting;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.Locale;

final class TransactionReport {
    private static final int LRECL = 133;
    private static final int PAGE_SIZE = 20;
    private static final String RULE = "-".repeat(LRECL);
    private static final BigDecimal TOTAL_MODULUS = BigDecimal.TEN.pow(9);

    private final Writer output;
    private final String start;
    private final String end;
    private int lines;
    private BigDecimal page = BigDecimal.ZERO;
    private BigDecimal account = BigDecimal.ZERO;
    private BigDecimal grand = BigDecimal.ZERO;
    private BigDecimal lastAmount;

    TransactionReport(Writer output, String start, String end) {
        this.output = output;
        this.start = start;
        this.end = end;
    }

    void detail(String id, long accountId, String type, String typeLabel, int category, String categoryLabel,
            String source, BigDecimal amount) throws IOException {
        if (lastAmount == null) headers();
        if (lines % PAGE_SIZE == 0) {
            endPage();
            headers();
        }
        page = add(page, amount);
        account = add(account, amount);
        lastAmount = amount;
        write(String.format(Locale.ROOT, "%s %011d %s-%s %s-%s %s    %s  ",
                field(id, 16), accountId, field(type, 2), field(typeLabel, 15), categoryCode(category),
                field(categoryLabel, 29), field(source, 10), editedAmount(amount, false)));
    }

    void endAccount() throws IOException {
        if (lastAmount == null) return;
        total("Account Total", account);
        account = BigDecimal.ZERO;
        write(RULE);
    }

    void endReport(StringBuilder sysout) throws IOException {
        if (lastAmount == null) return;
        sysout.append("TRAN-AMT ").append(displayAmount(lastAmount)).append('\n');
        sysout.append("WS-PAGE-TOTAL").append(displayAmount(page)).append('\n');
        // At EOF COBOL adds the retained last detail again and never writes a final account total.
        page = add(page, lastAmount);
        endPage();
        total("Grand Total", grand);
    }

    private void headers() throws IOException {
        write(field("DALYREPT", 38) + field("Daily Transaction Report", 41)
                + "Date Range: " + start + " to " + end);
        write("");
        write(field("Transaction ID", 17) + field("Account ID", 12) + field("Transaction Type", 19)
                + field("Tran Category", 35) + field("Tran Source", 15) + "        Amount");
        write(RULE);
    }

    private void endPage() throws IOException {
        total("Page Total ", page);
        grand = add(grand, page);
        page = BigDecimal.ZERO;
        write(RULE);
    }

    private void total(String label, BigDecimal amount) throws IOException {
        write(label + ".".repeat(97 - label.length()) + editedAmount(amount, true));
    }

    private void write(String line) throws IOException {
        if (line.length() > LRECL) throw new IllegalArgumentException("Report record exceeds 133 columns");
        // Transport trims trailing spaces from each fixed-width record, including blank lines.
        int end = line.length();
        while (end > 0 && line.charAt(end - 1) == ' ') end--;
        output.write(line, 0, end);
        output.write('\n');
        lines++;
    }

    private static BigDecimal add(BigDecimal a, BigDecimal b) {
        return a.add(b).remainder(TOTAL_MODULUS);
    }

    private static String editedAmount(BigDecimal amount, boolean signed) {
        if (amount.signum() == 0) return " ".repeat(15);
        String digits = String.format(Locale.ROOT, "%,.2f", amount.abs());
        if (digits.startsWith("0.")) digits = digits.substring(1);
        char sign = amount.signum() < 0 ? '-' : signed ? '+' : ' ';
        return sign + " ".repeat(14 - digits.length()) + digits;
    }

    private static String displayAmount(BigDecimal amount) {
        return String.format(Locale.ROOT, "%011d%c", amount.abs().movePointRight(2).longValueExact(),
                amount.signum() < 0 ? '-' : '+');
    }

    static String categoryCode(int category) {
        return String.format(Locale.ROOT, "%04d", category);
    }

    static String field(String value, int width) {
        if (value.length() >= width) return value.substring(0, width);
        return value + " ".repeat(width - value.length());
    }
}
