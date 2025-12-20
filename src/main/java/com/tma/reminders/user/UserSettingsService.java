package com.tma.reminders.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserSettingsService {

    private static final Long SINGLETON_ID = 1L;

    private final UserSettingsRepository repository;

    public UserSettingsService(UserSettingsRepository repository) {
        this.repository = repository;
    }

    public Optional<String> getChatId() {
        return repository.findById(SINGLETON_ID).map(UserSettings::getChatId).filter(id -> id != null && !id.isBlank());
    }

    @Transactional
    public String updateChatId(String chatId) {
        UserSettings settings = repository.findById(SINGLETON_ID).orElseGet(UserSettings::new);
        settings.setId(SINGLETON_ID);
        settings.setChatId(chatId);
        repository.save(settings);
        return chatId;
    }
}
