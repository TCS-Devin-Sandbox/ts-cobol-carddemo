package com.carddemo.trantype.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Tran Type code edit (COTRTUPC 1245-EDIT-NUM-REQD): required, numeric, not zero. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TranTypeCodeValidator.class)
public @interface TranTypeCode {

    String message() default "Tran Type code must be numeric.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
