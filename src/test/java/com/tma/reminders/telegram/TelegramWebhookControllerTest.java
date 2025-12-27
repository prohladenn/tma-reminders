package com.tma.reminders.telegram;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.tma.reminders.i18n.MessageService;
import com.tma.reminders.reminder.Recurrence;
import com.tma.reminders.reminder.Reminder;
import com.tma.reminders.reminder.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookControllerTest {

    @Mock
    private TelegramBotService telegramBotService;

    @Mock
    private ReminderService reminderService;

    @Mock
    private MessageService messageService;

    private TelegramWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new TelegramWebhookController(telegramBotService, reminderService, messageService);
    }

    @Test
    void createsReminderFromMessageText() {
        Update update = buildUpdateWithMessage("Pay rent; 2024-01-10 09:00; DAILY; Transfer", 100L);
        when(messageService.getForUser(eq("telegram.reminder.saved"), any()))
                .thenReturn("saved");

        controller.onUpdate(update);

        ArgumentCaptor<Reminder> reminderCaptor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderService).save(reminderCaptor.capture());
        Reminder saved = reminderCaptor.getValue();
        assertThat(saved.getChatId()).isEqualTo("100");
        assertThat(saved.getTitle()).isEqualTo("Pay rent");
        assertThat(saved.getStartTime()).isEqualTo(LocalDateTime.of(2024, 1, 10, 9, 0));
        assertThat(saved.getRecurrence()).isEqualTo(Recurrence.DAILY);
        assertThat(saved.getDescription()).isEqualTo("Transfer");
        verify(telegramBotService).sendMessage(100L, "saved");
    }

    @Test
    void listsRemindersFromTelegram() {
        Update update = buildUpdateWithMessage("/list", 200L);
        Reminder reminder = new Reminder();
        reminder.setId(22L);
        reminder.setTitle("Check reports");
        reminder.setStartTime(LocalDateTime.of(2024, 2, 2, 10, 30));
        reminder.setRecurrence(Recurrence.WEEKLY);

        when(reminderService.findAllByChatId("200")).thenReturn(List.of(reminder));
        when(messageService.getForUser("telegram.list.header")).thenReturn("header");
        when(messageService.getForUser(eq("telegram.list.item"), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    return "item %s %s %s %s".formatted(args[1], args[2], args[3], args[4]);
                });

        controller.onUpdate(update);

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramBotService).sendMessage(eq(200L), responseCaptor.capture());
        assertThat(responseCaptor.getValue())
                .contains("header")
                .contains("Check reports")
                .contains("WEEKLY");
    }

    @Test
    void deletesReminderFromTelegram() {
        Update update = buildUpdateWithMessage("/delete 42", 300L);
        when(messageService.getForUser("telegram.delete.success")).thenReturn("deleted");

        controller.onUpdate(update);

        verify(reminderService).delete(42L);
        verify(telegramBotService).sendMessage(300L, "deleted");
    }

    @Test
    void sendsDeleteErrorWhenIdInvalid() {
        Update update = buildUpdateWithMessage("/delete wrong", 400L);
        when(messageService.getForUser("telegram.delete.invalid")).thenReturn("invalid");

        controller.onUpdate(update);

        verifyNoInteractions(reminderService);
        verify(telegramBotService).sendMessage(400L, "invalid");
    }

    private Update buildUpdateWithMessage(String text, long chatId) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(update.callbackQuery()).thenReturn(null);
        when(message.text()).thenReturn(text);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);
        return update;
    }
}
