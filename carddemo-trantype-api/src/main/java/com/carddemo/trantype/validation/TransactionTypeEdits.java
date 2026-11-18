package com.carddemo.trantype.validation;

import com.carddemo.trantype.Messages;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Field edits ported from COTRTUPC 1245-EDIT-NUM-REQD / 1210-EDIT-TRANTYPE / 1230-EDIT-ALPHANUM-REQD
 * and COTRTLIC 1220-EDIT-TYPECD / 1230-EDIT-DESC / 1240-EDIT-ALPHANUM-REQD.
 */
public final class TransactionTypeEdits {

    public static final int DESCRIPTION_MAX = 50;
    private static final Pattern ONE_OR_TWO_DIGITS = Pattern.compile("\\d{1,2}");
    private static final Pattern TWO_DIGITS = Pattern.compile("\\d{2}");
    private static final Pattern ALPHANUM_SPACE = Pattern.compile("[A-Za-z0-9 ]*");

    private TransactionTypeEdits() {
    }

    /** Screen input of spaces, LOW-VALUES or '*' counts as not supplied (1100 / 1150). */
    static boolean isBlank(String value) {
        return value == null || value.isBlank() || "*".equals(value.trim());
    }

    /** Returns the edit message for a type code, or empty when valid. */
    public static Optional<String> checkTypeCode(String value) {
        if (isBlank(value)) {
            return Optional.of(Messages.CODE_REQUIRED);
        }
        String trimmed = value.trim();
        if (!ONE_OR_TWO_DIGITS.matcher(trimmed).matches()) {
            return Optional.of(Messages.CODE_NUMERIC);
        }
        if (Integer.parseInt(trimmed) == 0) {
            return Optional.of(Messages.CODE_NOT_ZERO);
        }
        return Optional.empty();
    }

    /** Validates and zero-pads to 2 digits ("1" -> "01"). */
    public static String normalizeTypeCode(String value) {
        checkTypeCode(value).ifPresent(msg -> {
            throw new FieldEditException(msg);
        });
        return String.format(java.util.Locale.ROOT, "%02d", Integer.parseInt(value.trim()));
    }

    /** Returns the edit message for a description, or empty when valid. */
    public static Optional<String> checkDescription(String value) {
        if (isBlank(value)) {
            return Optional.of(Messages.DESC_REQUIRED);
        }
        String trimmed = value.trim();
        if (!ALPHANUM_SPACE.matcher(trimmed).matches()) {
            return Optional.of(Messages.DESC_ALPHANUM);
        }
        if (trimmed.length() > DESCRIPTION_MAX) {
            return Optional.of(Messages.DESC_TOO_LONG);
        }
        return Optional.empty();
    }

    public static String normalizeDescription(String value) {
        checkDescription(value).ifPresent(msg -> {
            throw new FieldEditException(msg);
        });
        return value.trim();
    }

    /** CTLI type filter: blank or "00" = not supplied; otherwise exactly two digits. */
    public static Optional<String> parseTypeFilter(String value) {
        if (value == null || value.isBlank() || "00".equals(value.trim())) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (!TWO_DIGITS.matcher(trimmed).matches()) {
            throw new FieldEditException(Messages.TYPE_FILTER_INVALID);
        }
        return Optional.of(trimmed);
    }

    /** CTLI description filter: blank = not supplied; otherwise LIKE '%' || TRIM(x) || '%'. */
    public static Optional<String> parseDescriptionFilter(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.length() > DESCRIPTION_MAX) {
            throw new FieldEditException(Messages.DESC_FILTER_TOO_LONG);
        }
        return Optional.of("%" + trimmed + "%");
    }

    /** 1211 / 1205: equal when upper-cased trimmed values match. */
    public static boolean isSameDescription(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        return left.equalsIgnoreCase(right);
    }
}
