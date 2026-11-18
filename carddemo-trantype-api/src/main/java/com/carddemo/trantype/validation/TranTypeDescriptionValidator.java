package com.carddemo.trantype.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TranTypeDescriptionValidator implements ConstraintValidator<TranTypeDescription, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return TransactionTypeEdits.checkDescription(value)
                .map(message -> TranTypeCodeValidator.reject(context, message))
                .orElse(true);
    }
}
