package com.carddemo.trantype.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TranTypeCodeValidator implements ConstraintValidator<TranTypeCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return TransactionTypeEdits.checkTypeCode(value)
                .map(message -> reject(context, message))
                .orElse(true);
    }

    static boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
