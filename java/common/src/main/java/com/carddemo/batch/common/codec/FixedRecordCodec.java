package com.carddemo.batch.common.codec;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.carddemo.batch.common.codec.RecordLayout.Field;

/** Binary fields are raw bytes, not hexadecimal text, even in fixture .txt files. */
public final class FixedRecordCodec {
    /** Uninitialized numeric field filled with spaces, not a numeric zero. */
    public enum BlankNumeric { SPACES }

    private static final String POSITIVE = "{ABCDEFGHI";
    private static final String NEGATIVE = "}JKLMNOPQR";
    private final RecordLayout layout;
    private final String characters;
    private final boolean allowBlankNumerics;
    private final byte space;

    public FixedRecordCodec(RecordLayout layout) {
        this(layout, StandardCharsets.ISO_8859_1);
    }

    public FixedRecordCodec(RecordLayout layout, Charset charset) {
        this(layout, charset, false);
    }

    FixedRecordCodec(RecordLayout layout, Charset charset, boolean allowBlankNumerics) {
        if (!charset.equals(StandardCharsets.ISO_8859_1) && !charset.equals(Charset.forName("IBM037"))) {
            throw new IllegalArgumentException("Use Latin-1 (off-host) or IBM037 (EBCDIC)");
        }
        this.layout = layout;
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        var table = new StringBuilder(new String(bytes, charset));
        // IBM037 aliases NEL to LF; keep EBCDIC 0x15 distinct from 0x25.
        if (charset.equals(Charset.forName("IBM037"))) table.setCharAt(0x15, '\u0085');
        this.characters = table.toString();
        this.allowBlankNumerics = allowBlankNumerics;
        this.space = (byte) characters.indexOf(' ');
    }

    /** Strings retain padding and filler; initialized numerics have their declared scale. */
    public Map<String, Object> decode(byte[] record) {
        if (record.length != layout.length()) throw new IllegalArgumentException("Wrong record length");
        var values = new LinkedHashMap<String, Object>();
        int offset = 0;
        for (Field field : layout.fields()) {
            byte[] raw = Arrays.copyOfRange(record, offset, offset + field.length());
            Object value = field.kind() != RecordLayout.Kind.TEXT && allowBlankNumerics && allSpaces(raw)
                    ? BlankNumeric.SPACES : switch (field.kind()) {
                case TEXT -> decodeText(raw);
                case DISPLAY -> decodeDisplay(raw, field);
                case COMP3 -> decodePacked(raw, field);
                case COMP -> new BigDecimal(field.signed() ? new BigInteger(raw) : new BigInteger(1, raw), field.scale());
            };
            values.put(field.name(), value);
            offset += field.length();
        }
        return values;
    }

    private boolean allSpaces(byte[] raw) {
        for (byte b : raw) if (b != space) return false;
        return true;
    }

    /** No rounding, overflow truncation, implicit defaults, or text truncation. */
    public byte[] encode(Map<String, ?> values) {
        byte[] record = new byte[layout.length()];
        int offset = 0;
        for (Field field : layout.fields()) {
            Object value = values.get(field.name());
            byte[] raw;
            if (field.kind() == RecordLayout.Kind.TEXT) {
                if (!(value instanceof String text) || text.length() > field.length()) {
                    throw new IllegalArgumentException("Invalid text: " + field.name());
                }
                raw = encodeText(text + " ".repeat(field.length() - text.length()));
            } else if (allowBlankNumerics && value == BlankNumeric.SPACES) {
                raw = new byte[field.length()];
                Arrays.fill(raw, space);
            } else {
                if (!(value instanceof BigDecimal decimal)) throw new IllegalArgumentException("Expected BigDecimal: " + field.name());
                BigInteger units = decimal.setScale(field.scale()).unscaledValue();
                if ((!field.signed() && units.signum() < 0) || units.abs().toString().length() > field.digits()) {
                    throw new IllegalArgumentException("Numeric overflow: " + field.name());
                }
                raw = encodeNumber(units, field);
            }
            System.arraycopy(raw, 0, record, offset, raw.length);
            offset += field.length();
        }
        return record;
    }

    private BigDecimal decodeDisplay(byte[] raw, Field field) {
        String text = decodeText(raw);
        if (text.isBlank()) return BigDecimal.ZERO.setScale(field.scale());
        int sign = 1;
        char last = text.charAt(text.length() - 1);
        int digit = POSITIVE.indexOf(last);
        if (digit < 0) {
            digit = NEGATIVE.indexOf(last);
            if (digit >= 0) sign = -1;
        }
        if (digit >= 0) text = text.substring(0, text.length() - 1) + digit;
        text = text.replace(' ', '0');
        if (!text.matches("[0-9]+")) throw new IllegalArgumentException("Invalid zoned decimal: " + field.name());
        return new BigDecimal(new BigInteger(text).multiply(BigInteger.valueOf(sign)), field.scale());
    }

    private BigDecimal decodePacked(byte[] raw, Field field) {
        var digits = new StringBuilder();
        for (int i = 0; i < raw.length * 2 - 1; i++) {
            int digit = (raw[i / 2] >> (i % 2 == 0 ? 4 : 0)) & 15;
            if (digit > 9) throw new IllegalArgumentException("Invalid packed digit: " + field.name());
            digits.append(digit);
        }
        int sign = raw[raw.length - 1] & 15;
        if (sign < 10) throw new IllegalArgumentException("Invalid packed sign: " + field.name());
        BigInteger units = new BigInteger(digits.toString());
        if (sign == 11 || sign == 13) units = units.negate();
        if (units.abs().toString().length() > field.digits()) throw new IllegalArgumentException("Invalid packed padding");
        return new BigDecimal(units, field.scale());
    }

    private byte[] encodeNumber(BigInteger units, Field field) {
        if (field.kind() == RecordLayout.Kind.COMP) {
            int bits = field.length() * 8;
            if (units.bitLength() > bits - (field.signed() ? 1 : 0)) throw new IllegalArgumentException("Binary overflow");
            byte[] raw = new byte[field.length()];
            Arrays.fill(raw, units.signum() < 0 ? (byte) 0xff : 0);
            byte[] bytes = units.toByteArray();
            int count = Math.min(bytes.length, raw.length);
            System.arraycopy(bytes, bytes.length - count, raw, raw.length - count, count);
            return raw;
        }
        int width = field.kind() == RecordLayout.Kind.COMP3 ? field.length() * 2 - 1 : field.length();
        String digits = units.abs().toString();
        digits = "0".repeat(width - digits.length()) + digits;
        if (field.kind() == RecordLayout.Kind.DISPLAY) {
            if (field.signed()) digits = digits.substring(0, width - 1)
                    + (units.signum() < 0 ? NEGATIVE : POSITIVE).charAt(digits.charAt(width - 1) - '0');
            return encodeText(digits);
        }
        String nibbles = digits + (field.signed() ? units.signum() < 0 ? "D" : "C" : "F");
        return java.util.HexFormat.of().parseHex(nibbles);
    }

    private String decodeText(byte[] raw) {
        char[] text = new char[raw.length];
        for (int i = 0; i < raw.length; i++) text[i] = characters.charAt(Byte.toUnsignedInt(raw[i]));
        return new String(text);
    }

    private byte[] encodeText(String text) {
        byte[] raw = new byte[text.length()];
        for (int i = 0; i < raw.length; i++) {
            int value = characters.indexOf(text.charAt(i));
            if (value < 0) throw new IllegalArgumentException("Unmappable text character");
            raw[i] = (byte) value;
        }
        return raw;
    }
}
