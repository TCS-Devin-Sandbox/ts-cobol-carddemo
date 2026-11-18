package com.carddemo.trantype.service;

import com.carddemo.trantype.domain.TransactionType;

public record TransactionTypeDto(String trType, String trDescription) {

    public static TransactionTypeDto of(TransactionType t) {
        return new TransactionTypeDto(t.getTrType(), t.getTrDescription());
    }
}
