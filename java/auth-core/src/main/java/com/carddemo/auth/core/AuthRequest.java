package com.carddemo.auth.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

record AuthRequest(String date, String time, String card, String type, String expiry, String messageType,
                   String messageSource, int processingCode, BigDecimal amount, String category, String country,
                   int entryMode, String merchantId, String merchantName, String merchantCity, String merchantState,
                   String merchantZip, String transactionId) {
    static final BigDecimal ZERO = new BigDecimal("0.00");

    static AuthRequest parse(String csv) {
        String[] f = csv.split(",", -1);
        if (f.length < 18 || !StandardCharsets.ISO_8859_1.newEncoder().canEncode(csv)) {
            throw new IllegalArgumentException("Expected 18 CP00 fields in single-byte text");
        }
        return new AuthRequest(field(f[0], 6), field(f[1], 6), field(f[2], 16), field(f[3], 4),
                field(f[4], 4), field(f[5], 6), field(f[6], 6), digits(f[7], 6), amount(f[8]),
                field(f[9], 4), field(f[10], 3), digits(f[11], 2), field(f[12], 15), field(f[13], 22),
                field(f[14], 13), field(f[15], 2), field(f[16], 9), field(f[17], 15));
    }

    byte[] reply(String code, String reason, BigDecimal approved) {
        String amountDisplay = String.format(Locale.ROOT, "%14s", approved.toPlainString());
        return String.join(",", card, transactionId, time, code, reason, amountDisplay, " ")
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String field(String value, int width) {
        String clipped = value.substring(0, Math.min(width, value.length()));
        return clipped + " ".repeat(width - clipped.length());
    }

    private static int digits(String value, int width) {
        String numeric = value.strip();
        return numeric.isEmpty() ? 0 : Integer.parseUnsignedInt(numeric.substring(0, Math.min(width, numeric.length())));
    }

    private static BigDecimal amount(String value) {
        String numeric = field(value, 13).replace(" ", "");
        if (numeric.endsWith("-") || numeric.endsWith("CR") || numeric.endsWith("DB")) {
            numeric = "-" + numeric.replaceFirst("(?:-|CR|DB)$", "");
        } else if (numeric.endsWith("+")) {
            numeric = numeric.substring(0, numeric.length() - 1);
        }
        return numeric.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")
                ? new BigDecimal(numeric).setScale(2, RoundingMode.DOWN).remainder(new BigDecimal("10000000000"))
                : ZERO;
    }
}
