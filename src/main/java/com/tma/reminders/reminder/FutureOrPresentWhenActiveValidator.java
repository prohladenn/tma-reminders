package com.tma.reminders.reminder;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class FutureOrPresentWhenActiveValidator implements ConstraintValidator<FutureOrPresentWhenActive, Reminder> {

    @Override
    public boolean isValid(Reminder reminder, ConstraintValidatorContext context) {
        if (reminder == null) {
            return true;
        }
        if (!reminder.isActive()) {
            return true;
        }
        LocalDateTime startTime = reminder.getStartTime();
        return startTime != null && !startTime.isBefore(LocalDateTime.now(ZoneOffset.UTC));
    }
}
