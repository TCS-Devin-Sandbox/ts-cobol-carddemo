package com.carddemo.trantype.web;

import com.carddemo.trantype.web.validation.TranTypeCode;
import com.carddemo.trantype.web.validation.TranTypeDescription;

public record TransactionTypeCreateRequest(@TranTypeCode String trType, @TranTypeDescription String trDescription) {
}
