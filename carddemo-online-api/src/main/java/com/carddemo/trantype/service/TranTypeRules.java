package com.carddemo.trantype.service;

import com.carddemo.trantype.validation.TransactionTypeEdits;
import java.util.Optional;

public final class TranTypeRules {
    public static final int DESC_MAX = TransactionTypeEdits.DESC_MAX;

    private TranTypeRules() {
    }

    public static Optional<String> typeCodeError(String raw) {
        return TransactionTypeEdits.typeCodeError(raw);
    }

    public static String normalizeTypeCode(String raw) {
        return TransactionTypeEdits.normalizeTypeCode(raw);
    }

    public static Optional<String> descriptionError(String raw) {
        return TransactionTypeEdits.descriptionError(raw);
    }

    public static String normalizeDescription(String raw) {
        return TransactionTypeEdits.normalizeDescription(raw);
    }

    public static String typeFilter(String raw) {
        return TransactionTypeEdits.typeFilter(raw);
    }

    public static String descFilter(String raw) {
        return TransactionTypeEdits.descFilter(raw);
    }

    public static boolean sameDescription(String a, String b) {
        return TransactionTypeEdits.sameDescription(a, b);
    }
}
