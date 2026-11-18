package com.carddemo.trantype.service;

import java.util.Locale;

public enum PageDirection {
    FORWARD,
    BACKWARD;

    public static PageDirection parse(String value) {
        if (value == null || value.isBlank()) {
            return FORWARD;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("direction must be forward or backward");
        }
    }
}
