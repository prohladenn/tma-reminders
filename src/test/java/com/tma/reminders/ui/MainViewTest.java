package com.tma.reminders.ui;

import com.tma.reminders.i18n.MessageService;
import com.tma.reminders.reminder.ReminderService;
import com.tma.reminders.telegram.TelegramInitDataService;
import com.tma.reminders.user.UserSettings;
import com.tma.reminders.user.UserSettingsService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MainViewTest {

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    void constructorInitializesLabelsBeforeRemindersTitleExists() {
        UI.setCurrent(new UI());
        ReminderService reminderService = mock(ReminderService.class);
        when(reminderService.findAll()).thenReturn(Collections.emptyList());

        TelegramInitDataService telegramInitDataService = mock(TelegramInitDataService.class);
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        when(userSettingsService.getSettings()).thenReturn(new UserSettings());

        MessageService messageService = mock(MessageService.class);
        when(messageService.get(any(Locale.class), anyString(), any(Object[].class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        MainView view = assertDoesNotThrow(() -> new MainView(
                reminderService,
                telegramInitDataService,
                userSettingsService,
                messageService
        ));

        H2 remindersTitle = (H2) ReflectionTestUtils.getField(view, "remindersTitle");
        assertEquals("section.reminders", remindersTitle.getText());
    }
}
