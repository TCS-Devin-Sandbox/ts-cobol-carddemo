package com.carddemo.trantype.validation;

/** A COBOL input-edit failure; maps to HTTP 400 VALIDATION_ERROR with the source message. */
public class FieldEditException extends RuntimeException {

    public FieldEditException(String message) {
        super(message);
    }
}
