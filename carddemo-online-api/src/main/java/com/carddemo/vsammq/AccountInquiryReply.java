package com.carddemo.vsammq;

import java.math.BigDecimal;

/** COACCT01 request and reply messages: fixed 1000-byte strings, built as 4000-PROCESS-REQUEST-REPLY does. */
public final class AccountInquiryReply {
    public static final int MESSAGE_LENGTH = 1000;
    public static final String INQUIRY_FUNCTION = "INQA";

    /** ACCOUNT-RECORD (CVACT01Y) fields that WS-ACCT-RESPONSE carries. */
    public record Account(long accountId, String activeStatus, BigDecimal currentBalance, BigDecimal creditLimit,
                          BigDecimal cashCreditLimit, String openDate, String expirationDate, String reissueDate,
                          BigDecimal currentCycleCredit, BigDecimal currentCycleDebit, String accountGroupId) { }

    /** REQUEST-MSG-COPY: WS-FUNC X(4), WS-KEY 9(11), filler X(985). */
    public record Request(String function, String key) {
        public static Request parse(String message) {
            String padded = pad(message, MESSAGE_LENGTH);
            return new Request(padded.substring(0, 4), padded.substring(4, 15));
        }

        /** {@code WS-FUNC = 'INQA' AND WS-KEY > ZEROES}; a non-digit key is never a valid inquiry. */
        public boolean isInquiry() {
            return INQUIRY_FUNCTION.equals(function) && key.chars().allMatch(c -> c >= '0' && c <= '9')
                    && key.chars().anyMatch(c -> c != '0');
        }

        public long accountId() {
            return Long.parseLong(key);
        }
    }

    private AccountInquiryReply() { }

    /** DFHRESP(NORMAL): WS-ACCT-RESPONSE (252 bytes) moved to a space-filled REPLY-MESSAGE. */
    public static String found(Account a) {
        String response = "ACCOUNT ID : " + unsigned(a.accountId(), 11)
                + "ACCOUNT STATUS : " + text(a.activeStatus(), 1)
                + "BALANCE : " + signed(a.currentBalance())
                + "CREDIT LIMIT : " + signed(a.creditLimit())
                + "CASH LIMIT : " + signed(a.cashCreditLimit())
                + "OPEN DATE : " + text(a.openDate(), 10)
                + "EXPR DATE : " + text(a.expirationDate(), 10)
                + "REIS DATE : " + text(a.reissueDate(), 10)
                + "CREDIT BAL : " + signed(a.currentCycleCredit())
                + "DEBIT BAL : " + signed(a.currentCycleDebit())
                + "GROUP ID : " + text(a.accountGroupId(), 10);
        return pad(response, MESSAGE_LENGTH);
    }

    /** DFHRESP(NOTFND). */
    public static String notFound(Request request) {
        return pad("INVALID REQUEST PARAMETERS ACCT ID : " + request.key(), MESSAGE_LENGTH);
    }

    /** Function other than INQA, or a zero key: the key and function bytes are echoed as received. */
    public static String invalid(Request request) {
        return pad("INVALID REQUEST PARAMETERS ACCT ID : " + request.key() + "FUNCTION : " + request.function(),
                MESSAGE_LENGTH);
    }

    /** PIC S9(10)V99 display: 12 digits, sign overpunched on the last one (preferred sign C/D, EBCDIC rendering). */
    static String signed(BigDecimal value) {
        BigDecimal cents = value.movePointRight(2);
        if (cents.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("More than two decimal places: " + value);
        }
        String digits = cents.toBigInteger().abs().toString();
        if (digits.length() > 12) {
            throw new IllegalArgumentException("Does not fit S9(10)V99: " + value);
        }
        digits = "0".repeat(12 - digits.length()) + digits;
        int last = digits.charAt(11) - '0';
        char overpunch = cents.signum() < 0
                ? (last == 0 ? '}' : (char) ('J' + last - 1))
                : (last == 0 ? '{' : (char) ('A' + last - 1));
        return digits.substring(0, 11) + overpunch;
    }

    static String unsigned(long value, int width) {
        String digits = Long.toString(value);
        if (value < 0 || digits.length() > width) {
            throw new IllegalArgumentException("Does not fit 9(" + width + "): " + value);
        }
        return "0".repeat(width - digits.length()) + digits;
    }

    static String text(String value, int width) {
        String v = value == null ? "" : value;
        return v.length() >= width ? v.substring(0, width) : pad(v, width);
    }

    static String pad(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }
}
