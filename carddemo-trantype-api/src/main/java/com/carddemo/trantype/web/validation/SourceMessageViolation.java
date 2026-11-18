package com.carddemo.trantype.web.validation;

import jakarta.validation.ConstraintValidatorContext;
import java.util.Optional;

final class SourceMessageViolation {

    private SourceMessageViolation() {
    }

    static boolean apply(Optional<String> error, ConstraintValidatorContext context) {
        if (error.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(error.get().replace("{", "\\{").replace("$", "\\$"))
                .addConstraintViolation();
        return false;
    }
}
