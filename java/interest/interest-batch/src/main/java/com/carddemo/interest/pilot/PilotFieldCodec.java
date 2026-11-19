package com.carddemo.interest.pilot;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Pilot-only field boundary for the lab fixture profile (INT-MAP framing: one ASCII record per line, signed
 * zoned DISPLAY with GnuCOBOL ASCII overpunch on the last byte). Not the generic codec (INT-03 owns that);
 * EBCDIC, COMP-3 and host sign nibbles are out of scope and rejected rather than guessed.
 */
final class PilotFieldCodec {

    static final Charset LAB_PROFILE = StandardCharsets.ISO_8859_1;
    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private PilotFieldCodec() {
    }

    static String text(byte[] record, int offset, int length) {
        return new String(record, offset, length, LAB_PROFILE);
    }

    static byte[] slice(byte[] record, int offset, int length) {
        return Arrays.copyOfRange(record, offset, offset + length);
    }

    /** Unsigned DISPLAY numeric (PIC 9(n)) kept as text so its digit width is preserved. */
    static String unsignedDigits(byte[] record, int offset, int length, String dataset, long recordNumber) {
        for (int i = offset; i < offset + length; i++) {
            if (record[i] < '0' || record[i] > '9') {
                throw new PilotFailure.UnreadableInput(dataset, recordNumber, i, "non-digit in PIC 9 field");
            }
        }
        return text(record, offset, length);
    }

    /** Signed zoned DISPLAY numeric (PIC S9(i)V9(s)), trailing overpunch sign. */
    static BigDecimal signedZoned(byte[] record, int offset, int length, int scale, String dataset, long recordNumber) {
        StringBuilder digits = new StringBuilder(length);
        for (int i = offset; i < offset + length - 1; i++) {
            if (record[i] < '0' || record[i] > '9') {
                throw new PilotFailure.UnreadableInput(dataset, recordNumber, i, "non-digit in signed zoned field");
            }
            digits.append((char) record[i]);
        }
        char last = (char) (record[offset + length - 1] & 0xFF);
        int positive = POSITIVE_OVERPUNCH.indexOf(last);
        int negative = NEGATIVE_OVERPUNCH.indexOf(last);
        if (positive < 0 && negative < 0) {
            throw new PilotFailure.UnreadableInput(dataset, recordNumber, offset + length - 1,
                    "sign byte outside the lab overpunch profile");
        }
        digits.append(positive >= 0 ? positive : negative);
        BigDecimal value = new BigDecimal(new BigInteger(digits.toString()), scale);
        return negative >= 0 ? value.negate() : value;
    }

    /** Encodes a value already stored in its receiver (see {@link Receiver}) as signed zoned DISPLAY. */
    static byte[] encodeSignedZoned(BigDecimal value, int length, int scale) {
        BigInteger unscaled = value.setScale(scale, RoundingMode.UNNECESSARY).unscaledValue();
        String digits = unscaled.abs().toString();
        if (digits.length() > length) {
            throw new IllegalArgumentException("value wider than its receiver; store it through Receiver first");
        }
        char[] out = ("0".repeat(length - digits.length()) + digits).toCharArray();
        int lastDigit = out[length - 1] - '0';
        out[length - 1] = (unscaled.signum() < 0 ? NEGATIVE_OVERPUNCH : POSITIVE_OVERPUNCH).charAt(lastDigit);
        return new String(out).getBytes(LAB_PROFILE);
    }

    /** MOVE alphanumeric: left-justify, pad with spaces, truncate on the right. */
    static byte[] fixedText(String value, int length) {
        byte[] out = new byte[length];
        Arrays.fill(out, (byte) ' ');
        byte[] src = value.getBytes(LAB_PROFILE);
        System.arraycopy(src, 0, out, 0, Math.min(src.length, length));
        return out;
    }
}
