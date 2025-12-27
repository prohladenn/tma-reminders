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
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
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
        lenient().when(messageService.getForUser("keyboard.completed")).thenReturn("Done");
        lenient().when(userSettingsService.getSettings()).thenReturn(new UserSettings());
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

    @Test
    void savesReminderWithDefaultsAndChatIdFromSettings() {
        Reminder reminder = new Reminder();
        reminder.setTitle("Standup");
        reminder.setStartTime(BASE_TIME);
        reminder.setRecurrence(Recurrence.DAILY);

        when(userSettingsService.getChatId()).thenReturn(Optional.of("77"));
        doAnswer(invocation -> invocation.getArgument(0)).when(repository).save(any(Reminder.class));

        Reminder saved = reminderService.save(reminder);

        assertThat(saved.getChatId()).isEqualTo("77");
        assertThat(saved.getNextAttemptAt()).isEqualTo(BASE_TIME);
        assertThat(saved.getSendAttempts()).isZero();
        assertThat(saved.getLastSentAt()).isNull();
        assertThat(saved.getLastSentMessageId()).isNull();
    }

    @Test
    void schedulesRetryAfterFailedSendBeforeMaxAttempts() {
        UserSettings settings = new UserSettings();
        settings.setMaxRetryCount(1);
        when(userSettingsService.getSettings()).thenReturn(settings);
        Reminder reminder = buildReminder(Recurrence.ONCE);
        when(repository.findDueReminders(any())).thenReturn(List.of(reminder));
        when(telegramBotService.sendMessage(eq(1L), anyString(), any()))
                .thenReturn(SendResult.error(500, "down"));

        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        reminderService.dispatchDueReminders();
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

        assertThat(reminder.getSendAttempts()).isEqualTo(1);
        assertThat(reminder.getNextAttemptAt()).isAfterOrEqualTo(before.plusMinutes(2));
        assertThat(reminder.getNextAttemptAt()).isBeforeOrEqualTo(after.plusMinutes(2).plusSeconds(1));
        assertThat(reminder.isActive()).isTrue();
    }

    @Test
    void deactivatesOnceReminderAfterFinalFailure() {
        UserSettings settings = new UserSettings();
        settings.setMaxRetryCount(0);
        when(userSettingsService.getSettings()).thenReturn(settings);
        Reminder reminder = buildReminder(Recurrence.ONCE);
        when(repository.findDueReminders(any())).thenReturn(List.of(reminder));
        when(telegramBotService.sendMessage(eq(1L), anyString(), any()))
                .thenReturn(SendResult.error(500, "down"));

        reminderService.dispatchDueReminders();

        assertThat(reminder.isActive()).isFalse();
        assertThat(reminder.getNextAttemptAt()).isNull();
        assertThat(reminder.getSendAttempts()).isEqualTo(1);
    }

    @Test
    void advancesRecurringReminderAfterFinalFailure() {
        UserSettings settings = new UserSettings();
        settings.setMaxRetryCount(0);
        when(userSettingsService.getSettings()).thenReturn(settings);
        Reminder reminder = buildReminder(Recurrence.WEEKLY);
        when(repository.findDueReminders(any())).thenReturn(List.of(reminder));
        when(telegramBotService.sendMessage(eq(1L), anyString(), any()))
                .thenReturn(SendResult.error(500, "down"));

        reminderService.dispatchDueReminders();

        assertThat(reminder.getStartTime()).isEqualTo(BASE_TIME.plusWeeks(1));
        assertThat(reminder.getNextAttemptAt()).isEqualTo(BASE_TIME.plusWeeks(1));
        assertThat(reminder.getSendAttempts()).isZero();
        assertThat(reminder.getLastSentAt()).isNull();
        assertThat(reminder.isActive()).isTrue();
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
