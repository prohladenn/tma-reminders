package com.tma.reminders.reminder;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = FutureOrPresentWhenActiveValidator.class)
@Documented
public @interface FutureOrPresentWhenActive {

    String message() default "Start time must be in the present or future for active reminders";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
