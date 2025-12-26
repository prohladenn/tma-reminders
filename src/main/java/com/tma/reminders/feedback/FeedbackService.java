package com.tma.reminders.feedback;

import com.tma.reminders.user.UserSettings;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class FeedbackService {

    private final FeedbackRepository repository;

    public FeedbackService(FeedbackRepository repository) {
        this.repository = repository;
    }

    public Feedback saveFeedback(String message, UserSettings settings, Locale locale) {
        Feedback feedback = new Feedback();
        feedback.setMessage(message);
        if (settings != null) {
            feedback.setChatId(settings.getChatId());
            feedback.setTimeZoneId(settings.getTimeZoneId());
        }
        if (locale != null) {
            feedback.setLocale(locale.toLanguageTag());
        }
        feedback.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return repository.save(feedback);
    }
}
