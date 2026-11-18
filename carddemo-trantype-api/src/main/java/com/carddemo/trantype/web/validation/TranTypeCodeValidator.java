package com.carddemo.trantype.web.validation;

import com.carddemo.trantype.service.TranTypeRules;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TranTypeCodeValidator implements ConstraintValidator<TranTypeCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return SourceMessageViolation.apply(TranTypeRules.typeCodeError(value), context);
    }
}
