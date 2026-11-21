package com.carddemo.trantype.web;

import com.carddemo.trantype.web.validation.TranTypeDescription;

public record TransactionTypeUpdateRequest(@TranTypeDescription String trDescription) {
}
