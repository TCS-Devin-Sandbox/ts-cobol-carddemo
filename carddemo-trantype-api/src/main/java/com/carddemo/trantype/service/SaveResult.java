package com.carddemo.trantype.service;

import com.carddemo.trantype.web.dto.TransactionTypeDto;

/** Outcome of an update: {@code inserted} is true when the upsert fell through to INSERT (9600 → 9700). */
public record SaveResult(TransactionTypeDto transactionType, boolean inserted) {
}
