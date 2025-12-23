package com.tma.reminders.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserSettingsService {

    private static final Long SINGLETON_ID = 1L;
    private static final String DEFAULT_TIME_ZONE = "UTC";
    private static final int DEFAULT_MAX_RETRY_COUNT = 2;
    private static final LocalTime DEFAULT_QUIET_HOURS_START = LocalTime.of(22, 0);
    private static final LocalTime DEFAULT_QUIET_HOURS_END = LocalTime.of(7, 0);

    private final UserSettingsRepository repository;

    public UserSettingsService(UserSettingsRepository repository) {
        this.repository = repository;
    }

    public Optional<String> getChatId() {
        return repository.findById(SINGLETON_ID).map(UserSettings::getChatId).filter(id -> id != null && !id.isBlank());
    }

    public UserSettings getSettings() {
        UserSettings settings = repository.findById(SINGLETON_ID).orElseGet(UserSettings::new);
        boolean updated = applyDefaults(settings);
        if (updated) {
            settings.setId(SINGLETON_ID);
            settings = repository.save(settings);
        }
        return settings;
    }

    @Transactional
    public String updateChatId(String chatId) {
        UserSettings settings = repository.findById(SINGLETON_ID).orElseGet(UserSettings::new);
        settings.setId(SINGLETON_ID);
        settings.setChatId(chatId);
        applyDefaults(settings);
        repository.save(settings);
        return chatId;
    }

    @Transactional
    public UserSettings updateSettings(UserSettings updatedSettings) {
        UserSettings settings = repository.findById(SINGLETON_ID).orElseGet(UserSettings::new);
        settings.setId(SINGLETON_ID);
        if (updatedSettings.getChatId() != null && !updatedSettings.getChatId().isBlank()) {
            settings.setChatId(updatedSettings.getChatId());
        }
        settings.setTimeZoneId(updatedSettings.getTimeZoneId());
        settings.setMaxRetryCount(updatedSettings.getMaxRetryCount());
        settings.setQuietHoursStart(updatedSettings.getQuietHoursStart());
        settings.setQuietHoursEnd(updatedSettings.getQuietHoursEnd());
        settings.setLocale(updatedSettings.getLocale());
        applyDefaults(settings);
        return repository.save(settings);
    }

    private boolean applyDefaults(UserSettings settings) {
        boolean updated = false;
        if (settings.getTimeZoneId() == null || settings.getTimeZoneId().isBlank() || !isValidZone(settings.getTimeZoneId())) {
            settings.setTimeZoneId(DEFAULT_TIME_ZONE);
            updated = true;
        }
        if (settings.getMaxRetryCount() == null || settings.getMaxRetryCount() < 0) {
            settings.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
            updated = true;
        }
        if (settings.getQuietHoursStart() == null) {
            settings.setQuietHoursStart(DEFAULT_QUIET_HOURS_START);
            updated = true;
        }
        if (settings.getQuietHoursEnd() == null) {
            settings.setQuietHoursEnd(DEFAULT_QUIET_HOURS_END);
            updated = true;
        }
        if (settings.getLocale() == null || settings.getLocale().isBlank()) {
            settings.setLocale(Locale.getDefault().toLanguageTag());
            updated = true;
        }
        return updated;
    }

    private boolean isValidZone(String timeZoneId) {
        try {
            ZoneId.of(timeZoneId);
            return true;
        } catch (DateTimeException ex) {
            return false;
        }
    }
}
