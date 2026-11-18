package com.carddemo.trantype.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TranTypeCodeValidator implements ConstraintValidator<TranTypeCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String error = TranTypeCodes.validate(value);
        if (error == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
        return false;
    }
}
