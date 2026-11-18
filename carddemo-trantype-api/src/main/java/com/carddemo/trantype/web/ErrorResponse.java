package com.carddemo.trantype.web;

import com.carddemo.trantype.service.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(ErrorCode code, String message, Integer sqlcode) {
}
