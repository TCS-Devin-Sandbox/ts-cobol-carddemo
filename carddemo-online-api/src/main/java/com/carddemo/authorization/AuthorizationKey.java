package com.carddemo.authorization;

import java.util.Locale;
import java.util.regex.Pattern;

/** PA-AUTHORIZATION-KEY (PAUT9CTS): inverted date S9(5) and time S9(9), as 5 + 9 digits. */
record AuthorizationKey(int dateInverted, long timeInverted) {
    private static final Pattern TEXT = Pattern.compile("[0-9]{14}");

    static AuthorizationKey parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (!TEXT.matcher(text).matches()) {
            throw new IllegalArgumentException("Invalid authorization key");
        }
        return new AuthorizationKey(Integer.parseInt(text.substring(0, 5)), Long.parseLong(text.substring(5)));
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%05d%09d", dateInverted, timeInverted);
    }
}
