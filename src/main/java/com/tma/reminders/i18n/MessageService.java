package com.tma.reminders.i18n;

import com.tma.reminders.user.UserSettings;
import com.tma.reminders.user.UserSettingsService;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

@Service
public class MessageService {

    private final UserSettingsService userSettingsService;

    public MessageService(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    public String get(Locale locale, String key, Object... args) {
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
        String pattern = bundle.getString(key);
        return MessageFormat.format(pattern, args);
    }

    public String getForUser(String key, Object... args) {
        return get(resolveLocale(), key, args);
    }

    private Locale resolveLocale() {
        UserSettings settings = userSettingsService.getSettings();
        if (settings == null || settings.getLocale() == null || settings.getLocale().isBlank()) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(settings.getLocale());
    }
}
