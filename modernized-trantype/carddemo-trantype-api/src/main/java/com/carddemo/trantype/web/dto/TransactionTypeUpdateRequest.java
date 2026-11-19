package com.carddemo.trantype.web.dto;

import com.carddemo.trantype.validation.TranTypeDescription;

public record TransactionTypeUpdateRequest(@TranTypeDescription String trDescription) {
}
