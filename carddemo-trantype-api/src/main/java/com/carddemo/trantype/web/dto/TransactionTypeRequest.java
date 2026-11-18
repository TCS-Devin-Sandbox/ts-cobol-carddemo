package com.carddemo.trantype.web.dto;

import com.carddemo.trantype.validation.TranTypeCode;
import com.carddemo.trantype.validation.TranTypeDescription;

public record TransactionTypeRequest(@TranTypeCode String trType, @TranTypeDescription String trDescription) {
}
