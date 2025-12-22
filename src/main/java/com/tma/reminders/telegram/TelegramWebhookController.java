package com.tma.reminders.telegram;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.tma.reminders.reminder.Reminder;
import com.tma.reminders.reminder.ReminderService;
import com.tma.reminders.reminder.Recurrence;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/telegram")
public class TelegramWebhookController {

    private final TelegramBotService telegramBotService;
    private final ReminderService reminderService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public TelegramWebhookController(TelegramBotService telegramBotService, ReminderService reminderService) {
        this.telegramBotService = telegramBotService;
        this.reminderService = reminderService;
    }

    @PostMapping
    public ResponseEntity<Void> onUpdate(@RequestBody Update update) {
        Message message = update.message();
        handleMessage(message);
        handleCallback(update.callbackQuery());
        return ResponseEntity.ok().build();
    }

    @Scheduled(fixedDelayString = "${telegram.polling.delay-ms:3000}")
    public void pollUpdates() {
        // Simple long polling to make local development easier.
        List<Update> updates = telegramBotService.pollUpdates(20);
        if (updates != null) {
            updates.forEach(u -> {
                Message message = u.message();
                handleMessage(message);
                handleCallback(u.callbackQuery());
            });
        }
    }

    private void handleMessage(Message message) {
        if (message == null || message.text() == null) {
            return;
        }
        String chatId = String.valueOf(message.chat().id());
        String text = message.text();
        if (text.startsWith("/list")) {
            List<Reminder> reminders = reminderService.findAllByChatId(chatId);
            String response = reminders.isEmpty() ? "Нет активных напоминаний" : formatReminders(reminders);
            telegramBotService.sendMessage(message.chat().id(), response);
        } else if (text.startsWith("/delete")) {
            String[] parts = text.split(" ");
            if (parts.length >= 2) {
                try {
                    Long id = Long.parseLong(parts[1]);
                    reminderService.delete(id);
                    telegramBotService.sendMessage(message.chat().id(), "Напоминание удалено");
                } catch (NumberFormatException ex) {
                    telegramBotService.sendMessage(message.chat().id(), "Укажите корректный id: /delete 1");
                }
            }
        } else {
            createReminderFromText(message, chatId, text);
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.data() == null || callbackQuery.message() == null) {
            return;
        }
        String data = callbackQuery.data();
        if (data.startsWith("complete:")) {
            try {
                Long reminderId = Long.parseLong(data.substring("complete:".length()));
                String chatId = String.valueOf(callbackQuery.message().chat().id());
                boolean completed = reminderService.completeReminder(reminderId, chatId);
                if (completed) {
                    telegramBotService.removeKeyboard(callbackQuery.message().chat().id(), callbackQuery.message().messageId());
                    telegramBotService.sendMessage(callbackQuery.message().chat().id(), "Напоминание завершено");
                    telegramBotService.answerCallback(callbackQuery.id(), "Отмечено как выполнено");
                } else {
                    telegramBotService.answerCallback(callbackQuery.id(), "Напоминание не найдено");
                }
            } catch (NumberFormatException ex) {
                telegramBotService.answerCallback(callbackQuery.id(), "Некорректный идентификатор");
            }
        } else {
            telegramBotService.answerCallback(callbackQuery.id(), "Неизвестное действие");
        }
    }

    private void createReminderFromText(Message message, String chatId, String text) {
        String[] parts = text.split(";", 4);
        if (parts.length < 3) {
            telegramBotService.sendMessage(message.chat().id(), "Используйте формат: Заголовок; yyyy-MM-dd HH:mm; DAILY|WEEKLY|MONTHLY|ONCE; Описание");
            return;
        }
        try {
            Reminder reminder = new Reminder();
            reminder.setChatId(chatId);
            reminder.setTitle(parts[0].trim());
            reminder.setStartTime(LocalDateTime.parse(parts[1].trim(), formatter));
            reminder.setRecurrence(Recurrence.valueOf(parts[2].trim().toUpperCase()));
            if (parts.length == 4) {
                reminder.setDescription(parts[3].trim());
            }
            reminderService.save(reminder);
            telegramBotService.sendMessage(message.chat().id(), "Напоминание сохранено: " + reminder.getTitle());
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            telegramBotService.sendMessage(message.chat().id(), "Ошибка разбора. Проверьте дату и тип повторения.");
        }
    }

    private String formatReminders(List<Reminder> reminders) {
        StringBuilder sb = new StringBuilder("Ваши напоминания:\n");
        for (Reminder reminder : reminders) {
            sb.append(reminder.getId())
                    .append(": ")
                    .append(reminder.getTitle())
                    .append(" => ")
                    .append(reminder.getStartTime().format(formatter))
                    .append(" (" + reminder.getRecurrence() + ")\n");
        }
        return sb.toString();
    }
}
