package com.carddemo.trantype.service;

import com.carddemo.trantype.domain.TransactionType;

public record TransactionTypeDto(String trType, String description) {

    public static TransactionTypeDto from(TransactionType entity) {
        return new TransactionTypeDto(entity.getTrType(), entity.getDescription());
    }
}
