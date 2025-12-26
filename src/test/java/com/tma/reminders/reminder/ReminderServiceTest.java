package com.tma.reminders.reminder;

import com.tma.reminders.i18n.MessageService;
import com.tma.reminders.telegram.TelegramBotService;
import com.tma.reminders.telegram.TelegramBotService.SendResult;
import com.tma.reminders.user.UserSettings;
import com.tma.reminders.user.UserSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2024, 1, 1, 9, 0);

    @Mock
    private ReminderRepository repository;

    @Mock
    private TelegramBotService telegramBotService;

    @Mock
    private UserSettingsService userSettingsService;

    @Mock
    private MessageService messageService;

    private ReminderService reminderService;

    @BeforeEach
    void setUp() {
        reminderService = new ReminderService(repository, telegramBotService, userSettingsService, messageService);
        when(messageService.getForUser("keyboard.completed")).thenReturn("Done");
        when(userSettingsService.getSettings()).thenReturn(new UserSettings());
    }

    @ParameterizedTest
    @MethodSource("recurringSchedules")
    void advancesRecurringRemindersAfterSuccessfulSend(Recurrence recurrence, LocalDateTime expectedStart) {
        Reminder reminder = buildReminder(recurrence);
        when(repository.findDueReminders(any())).thenReturn(List.of(reminder));
        when(telegramBotService.sendMessage(eq(1L), anyString(), any())).thenReturn(SendResult.ok(12));

        reminderService.dispatchDueReminders();

        assertThat(reminder.getStartTime()).isEqualTo(expectedStart);
        assertThat(reminder.getNextAttemptAt()).isEqualTo(expectedStart);
        assertThat(reminder.getSendAttempts()).isZero();
        assertThat(reminder.getLastSentAt()).isNull();
        assertThat(reminder.getLastSentMessageId()).isNull();
        assertThat(reminder.isActive()).isTrue();
    }

    @Test
    void completesOnceRemindersAfterSuccessfulSend() {
        Reminder reminder = buildReminder(Recurrence.ONCE);
        when(repository.findDueReminders(any())).thenReturn(List.of(reminder));
        when(telegramBotService.sendMessage(eq(1L), anyString(), any())).thenReturn(SendResult.ok(12));

        reminderService.dispatchDueReminders();

        assertThat(reminder.getStartTime()).isEqualTo(BASE_TIME);
        assertThat(reminder.getNextAttemptAt()).isNull();
        assertThat(reminder.getSendAttempts()).isEqualTo(1);
        assertThat(reminder.getLastSentAt()).isNotNull();
        assertThat(reminder.isActive()).isFalse();
    }

    private static Stream<Arguments> recurringSchedules() {
        return Stream.of(
                Arguments.of(Recurrence.DAILY, BASE_TIME.plusDays(1)),
                Arguments.of(Recurrence.WEEKLY, BASE_TIME.plusWeeks(1)),
                Arguments.of(Recurrence.MONTHLY, BASE_TIME.plusMonths(1))
        );
    }

    private static Reminder buildReminder(Recurrence recurrence) {
        Reminder reminder = new Reminder();
        reminder.setChatId("1");
        reminder.setTitle("Test");
        reminder.setStartTime(BASE_TIME);
        reminder.setNextAttemptAt(BASE_TIME);
        reminder.setRecurrence(recurrence);
        reminder.setActive(true);
        return reminder;
    }
}
