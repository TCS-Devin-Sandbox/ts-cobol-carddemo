package com.carddemo.trantype.web.dto;

import com.carddemo.trantype.domain.TransactionType;

public record TransactionTypeDto(String trType, String trDescription) {

    public static TransactionTypeDto from(TransactionType entity) {
        return new TransactionTypeDto(entity.getTrType(), entity.getTrDescription());
    }
}
