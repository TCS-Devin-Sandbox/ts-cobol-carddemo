package com.carddemo.trantype.service;

import static com.carddemo.trantype.service.SourceMessages.*;

import java.util.Optional;
import java.util.regex.Pattern;

/** Field edits from COTRTUPC 1210/1230/1245 and COTRTLIC 1220/1230/1240. */
public final class TranTypeRules {

    public static final int DESC_MAX = 50;
    private static final Pattern NUMERIC = Pattern.compile("[+]?\\d{1,2}");
    private static final Pattern ALPHANUM_SPACES = Pattern.compile("[A-Za-z0-9 ]*");

    private TranTypeRules() {
    }

    /** Returns the error message for a type code, or empty when valid (1245-EDIT-NUM-REQD). */
    public static Optional<String> typeCodeError(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            return Optional.of(TYPE_CODE_NAME + MUST_BE_SUPPLIED);
        }
        if (!NUMERIC.matcher(v).matches()) {
            return Optional.of(TYPE_CODE_NAME + MUST_BE_NUMERIC);
        }
        if (Integer.parseInt(v.replace("+", "")) == 0) {
            return Optional.of(TYPE_CODE_NAME + MUST_NOT_BE_ZERO);
        }
        return Optional.empty();
    }

    /** Validates and zero-pads a type code to two digits (1210-EDIT-TRANTYPE). */
    public static String normalizeTypeCode(String raw) {
        typeCodeError(raw).ifPresent(m -> {
            throw ApiException.validation(m);
        });
        return pad(raw.trim());
    }

    /** Returns the error message for a description, or empty when valid (1230/1240-EDIT-ALPHANUM-REQD). */
    public static Optional<String> descriptionError(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            return Optional.of(DESC_NAME + MUST_BE_SUPPLIED);
        }
        if (!ALPHANUM_SPACES.matcher(v).matches()) {
            return Optional.of(DESC_NAME + ALPHANUM_ONLY);
        }
        if (v.length() > DESC_MAX) {
            return Optional.of(DESC_TOO_LONG);
        }
        return Optional.empty();
    }

    public static String normalizeDescription(String raw) {
        descriptionError(raw).ifPresent(m -> {
            throw ApiException.validation(m);
        });
        return raw.trim();
    }

    /** CTLI 1220-EDIT-TYPECD: blank or zero = no filter, numeric = exact, else error. */
    public static String typeFilter(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (!NUMERIC.matcher(v).matches()) {
            throw ApiException.validation(TYPE_FILTER_INVALID);
        }
        return Integer.parseInt(v.replace("+", "")) == 0 ? null : pad(v);
    }

    /** CTLI 1230-EDIT-DESC: '%' || TRIM(x) || '%'; blank = no filter. */
    public static String descFilter(String raw) {
        String v = raw == null ? "" : raw.trim();
        return v.isEmpty() ? null : "%" + v + "%";
    }

    /** 1205-COMPARE-OLD-NEW / 1211: upper-cased, trimmed equality. */
    public static boolean sameDescription(String a, String b) {
        return a.trim().toUpperCase().equals(b.trim().toUpperCase());
    }

    private static String pad(String v) {
        return String.format("%02d", Integer.parseInt(v.replace("+", "")));
    }
}
