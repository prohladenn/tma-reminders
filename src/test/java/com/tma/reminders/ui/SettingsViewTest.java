package com.tma.reminders.ui;

import com.tma.reminders.i18n.MessageService;
import com.tma.reminders.telegram.TelegramBotService;
import com.tma.reminders.telegram.TelegramInitDataService;
import com.tma.reminders.user.UserSettings;
import com.tma.reminders.user.UserSettingsService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsViewTest {

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    void constructorUsesEnglishMessagesForUiLabels() {
        UI.setCurrent(new UI());
        TelegramBotService telegramBotService = mock(TelegramBotService.class);
        TelegramInitDataService telegramInitDataService = mock(TelegramInitDataService.class);
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        UserSettings settings = new UserSettings();
        settings.setLocale("en-US");
        when(userSettingsService.getSettings()).thenReturn(settings);
        when(userSettingsService.getChatId()).thenReturn(Optional.empty());

        MessageService messageService = new MessageService(userSettingsService);

        SettingsView view = assertDoesNotThrow(() -> new SettingsView(
                telegramBotService,
                telegramInitDataService,
                userSettingsService,
                messageService
        ));

        TextField chatIdField = (TextField) ReflectionTestUtils.getField(view, "chatIdField");
        Button testMessageButton = (Button) ReflectionTestUtils.getField(view, "testMessageButton");
        H2 headerTitle = (H2) ReflectionTestUtils.getField(view, "headerTitle");

        Locale locale = Locale.forLanguageTag("en-US");
        assertEquals(messageService.get(locale, "placeholder.chatId"), chatIdField.getPlaceholder());
        assertEquals(messageService.get(locale, "button.testMessage"), testMessageButton.getText());
        assertEquals(messageService.get(locale, "section.settings"), headerTitle.getText());
    }

    @Test
    void constructorUsesRussianMessagesForUiLabels() {
        UI.setCurrent(new UI());
        TelegramBotService telegramBotService = mock(TelegramBotService.class);
        TelegramInitDataService telegramInitDataService = mock(TelegramInitDataService.class);
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        UserSettings settings = new UserSettings();
        settings.setLocale("ru-RU");
        when(userSettingsService.getSettings()).thenReturn(settings);
        when(userSettingsService.getChatId()).thenReturn(Optional.empty());

        MessageService messageService = new MessageService(userSettingsService);

        SettingsView view = assertDoesNotThrow(() -> new SettingsView(
                telegramBotService,
                telegramInitDataService,
                userSettingsService,
                messageService
        ));

        TextField chatIdField = (TextField) ReflectionTestUtils.getField(view, "chatIdField");
        Locale locale = Locale.forLanguageTag("ru-RU");
        assertEquals(messageService.get(locale, "placeholder.chatId"), chatIdField.getPlaceholder());
    }
}
