package com.carddemo.trantype.service;

public record SaveResult(TransactionTypeDto transactionType, boolean created) {
}
