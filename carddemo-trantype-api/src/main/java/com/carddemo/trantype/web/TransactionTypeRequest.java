package com.carddemo.trantype.web;

import com.carddemo.trantype.service.SourceMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** TRTYDSC / TRTYPDn: required, letters/digits/spaces only (1230 / 1240), BMS LENGTH=50. */
public record TransactionTypeRequest(
        @NotBlank(message = SourceMessages.DESC_REQUIRED)
        @Pattern(regexp = "^[A-Za-z0-9 ]*$", message = SourceMessages.DESC_ALPHANUMERIC)
        @Size(max = 50, message = SourceMessages.DESC_ALPHANUMERIC)
        String description) {
}
