package com.carddemo.trantype.validation;

import com.carddemo.trantype.service.SourceMessages;
import java.util.Locale;
import java.util.regex.Pattern;

/** CTTU 1210-EDIT-TRANTYPE / 1245-EDIT-NUM-REQD. */
public final class TranTypeCodes {

    private static final Pattern ONE_OR_TWO_DIGITS = Pattern.compile("\\d{1,2}");

    private TranTypeCodes() {
    }

    /** Returns the source error literal, or {@code null} when the value is a valid type code. */
    public static String validate(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().equals("*")) {
            return SourceMessages.TYPE_CODE_REQUIRED;
        }
        String value = raw.trim();
        if (!ONE_OR_TWO_DIGITS.matcher(value).matches()) {
            return SourceMessages.TYPE_CODE_NUMERIC;
        }
        if (Integer.parseInt(value) == 0) {
            return SourceMessages.TYPE_CODE_NOT_ZERO;
        }
        return null;
    }

    /** Zero-pads a valid code to two digits ({@code 5} becomes {@code 05}). */
    public static String normalise(String raw) {
        return String.format(Locale.ROOT, "%02d", Integer.parseInt(raw.trim()));
    }
}
