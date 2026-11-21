package com.carddemo.batch.statements;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/** CBSTM03A's ST-LINEnn report lines and HTML-* records (80 and 100 bytes, written trailing-space trimmed). */
final class StatementFormat {
    static final int TEXT_LRECL = 80;
    static final int HTML_LRECL = 100;
    private static final String DASHES = "-".repeat(80);
    private static final String HEADER_CELL = "<td colspan=\"3\" style=\"padding:0px 5px;";
    private static final String TRAN_CELL = "padding:0px 5px; background-color:";

    private StatementFormat() { }

    static void header(List<String> text, List<String> html, String name, String address1, String address2,
            String address3, String accountId, String balance, String fico) {
        text.add("*".repeat(31) + "START OF STATEMENT" + "*".repeat(31));
        html(html, "<!DOCTYPE html>", "<html lang=\"en\">", "<head>", "<meta charset=\"utf-8\">",
                "<title>HTML Table Layout</title>", "</head>", "<body style=\"margin:0px;\">",
                "<table  align=\"center\" frame=\"box\" style=\"width:70%; font:12px Segoe UI,sans-serif;\">",
                "<tr>", HEADER_CELL + "background-color:#1d1d96b3;\">",
                "<h3>Statement for Account Number: " + accountId + "</h3>", "</td>", "</tr>", "<tr>",
                HEADER_CELL + "background-color:#FFAF33;\">", "<p style=\"font-size:16px\">Bank of XYZ</p>",
                "<p>410 Terry Ave N</p>", "<p>Seattle WA 99999</p>", "</td>", "</tr>", "<tr>",
                HEADER_CELL + "background-color:#f2f2f2;\">");
        html(html, "<p style=\"font-size:16px\">" + untilDoubleSpace(StatementProgram.pad(name, 50)) + "  </p>",
                "<p>" + untilDoubleSpace(address1) + "  </p>", "<p>" + untilDoubleSpace(address2) + "  </p>",
                "<p>" + untilDoubleSpace(address3) + "  </p>", "</td>", "</tr>", "<tr>",
                HEADER_CELL + "background-color:#33FFD1; text-align:center;\">",
                "<p style=\"font-size:16px\">Basic Details</p>", "</td>", "</tr>", "<tr>",
                HEADER_CELL + "background-color:#f2f2f2;\">",
                "<p>Account ID         : " + untilAsterisk(accountId) + "</p>",
                "<p>Current Balance    : " + untilAsterisk(balance) + "</p>",
                "<p>FICO Score         : " + untilAsterisk(fico) + "</p>", "</td>", "</tr>", "<tr>",
                HEADER_CELL + "background-color:#33FFD1; text-align:center;\">",
                "<p style=\"font-size:16px\">Transaction Summary</p>", "</td>", "</tr>", "<tr>",
                tranCell(25, "#33FF5E", "left"), "<p style=\"font-size:16px\">Tran ID</p>", "</td>",
                tranCell(55, "#33FF5E", "left"), "<p style=\"font-size:16px\">Tran Details</p>", "</td>",
                tranCell(20, "#33FF5E", "right"), "<p style=\"font-size:16px\">Amount</p>", "</td>", "</tr>");
        text(text, name, address1, address2, address3, DASHES, " ".repeat(33) + "Basic Details", DASHES,
                "Account ID         :" + accountId, "Current Balance    :" + balance, "FICO Score         :" + fico,
                DASHES, " ".repeat(30) + "TRANSACTION SUMMARY", DASHES,
                "Tran ID         " + StatementProgram.pad("Tran Details    ", 51) + "  Tran Amount", DASHES);
    }

    static void transaction(List<String> text, List<String> html, String id, String details, String amount) {
        text(text, id + " " + details + "$" + amount);
        html(html, "<tr>", tranCell(25, "#f2f2f2", "left"), "<p>" + untilAsterisk(id) + "</p>", "</td>",
                tranCell(55, "#f2f2f2", "left"), "<p>" + untilAsterisk(details) + "</p>", "</td>",
                tranCell(20, "#f2f2f2", "right"), "<p>" + untilAsterisk(amount) + "</p>", "</td>", "</tr>");
    }

    static void trailer(List<String> text, List<String> html, String total) {
        text(text, DASHES, "Total EXP:" + " ".repeat(56) + "$" + total,
                "*".repeat(32) + "END OF STATEMENT" + "*".repeat(32));
        html(html, "<tr>", HEADER_CELL + "background-color:#1d1d96b3;\">", "<h3>End of Statement</h3>", "</td>",
                "</tr>", "</table>", "</body>", "</html>");
    }

    /** PIC Z(9).99- (no BLANK WHEN ZERO): high-order digits beyond nine are truncated. */
    static String zeroSuppressed(BigDecimal value) {
        String integer = value.abs().toBigInteger().mod(BigInteger.TEN.pow(9)).toString();
        return " ".repeat(9 - (integer.equals("0") ? 0 : integer.length()))
                + (integer.equals("0") ? "" : integer) + "." + cents(value) + (value.signum() < 0 ? "-" : " ");
    }

    /** PIC 9(9).99-: zero-filled, high-order digits beyond nine truncated. */
    static String unsignedEdited(BigDecimal value) {
        return digits(value.abs(), 9) + "." + cents(value) + (value.signum() < 0 ? "-" : " ");
    }

    /** MOVE of an unsigned integer PIC 9(n) to alphanumeric: zero-filled, truncated to n digits. */
    static String digits(BigDecimal value, int length) {
        String digits = value.abs().toBigInteger().toString();
        digits = "0".repeat(Math.max(0, length - digits.length())) + digits;
        return digits.substring(digits.length() - length);
    }

    /** STRING ... DELIMITED BY ' ', ' ' DELIMITED BY SIZE ... INTO a field of {@code length}. */
    static String concat(int length, String... parts) {
        return StatementProgram.pad(String.join("", parts), length);
    }

    static String untilSpace(String value) {
        return until(value, " ");
    }

    static String untilDoubleSpace(String value) {
        return until(value, "  ");
    }

    static String untilAsterisk(String value) {
        return until(value, "*");
    }

    private static String until(String value, String delimiter) {
        int end = value.indexOf(delimiter);
        return end < 0 ? value : value.substring(0, end);
    }

    private static String cents(BigDecimal value) {
        String cents = value.abs().movePointRight(2).toBigInteger().mod(BigInteger.valueOf(100)).toString();
        return cents.length() == 1 ? "0" + cents : cents;
    }

    private static String tranCell(int width, String color, String align) {
        return "<td style=\"width:" + width + "%; " + TRAN_CELL + color + "; text-align:" + align + ";\">";
    }

    private static void text(List<String> lines, String... records) {
        for (String record : records) lines.add(StatementProgram.pad(record, TEXT_LRECL).stripTrailing());
    }

    private static void html(List<String> lines, String... records) {
        for (String record : records) lines.add(StatementProgram.pad(record, HTML_LRECL).stripTrailing());
    }
}
