package com.carddemo.trantype.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Transaction Desc edit (1230/1240-EDIT-ALPHANUM-REQD): required, letters/digits/space, max 50. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TranTypeDescriptionValidator.class)
public @interface TranTypeDescription {

    String message() default "Transaction Desc can have numbers or alphabets only.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
