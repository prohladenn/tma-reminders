package com.tma.reminders.reminder;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.tma.reminders.telegram.TelegramBotService;
import com.tma.reminders.telegram.TelegramBotService.SendResult;
import com.tma.reminders.user.UserSettingsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final ReminderRepository repository;
    private final TelegramBotService telegramBotService;
    private final UserSettingsService userSettingsService;

    public ReminderService(ReminderRepository repository, TelegramBotService telegramBotService,
                           UserSettingsService userSettingsService) {
        this.repository = repository;
        this.telegramBotService = telegramBotService;
        this.userSettingsService = userSettingsService;
    }

    public Reminder save(Reminder reminder) {
        if (reminder.getChatId() == null || reminder.getChatId().isBlank()) {
            userSettingsService.getChatId().ifPresent(reminder::setChatId);
        }
        return repository.save(reminder);
    }

    public List<Reminder> findAllByChatId(String chatId) {
        return repository.findAllByChatIdOrderByStartTimeAsc(chatId);
    }

    public List<Reminder> findAll() {
        return repository.findAll();
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Scheduled(fixedRateString = "${reminders.scheduler.rate-ms:60000}")
    @Transactional
    public void dispatchDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> dueReminders = repository.findDueReminders(now);
        for (Reminder reminder : dueReminders) {
            SendResult result = telegramBotService.sendMessage(Long.valueOf(reminder.getChatId()), formatMessage(reminder),
                    completedKeyboard(reminder));
            if (result.isSuccess()) {
                processRecurrence(reminder);
            } else if (result.isNotFound()) {
                reminder.setActive(false);
                log.warn("Disabling reminder {} for chat {} because Telegram returned 404 ({}). Check that the bot is started and chat id is correct.",
                        reminder.getId(), reminder.getChatId(), result.description());
            } else {
                log.warn("Reminder {} not rescheduled because sending failed ({}). It will be retried on the next scheduler run.",
                        reminder.getId(), result.description());
            }
        }
    }

    @Transactional
    public boolean completeReminder(Long reminderId, String chatId) {
        return repository.findById(reminderId)
                .filter(reminder -> reminder.getChatId().equals(chatId))
                .map(reminder -> {
                    reminder.setActive(false);
                    reminder.setStartTime(LocalDateTime.now().plusSeconds(1));
                    return true;
                })
                .orElse(false);
    }

    private String formatMessage(Reminder reminder) {
        String description = reminder.getDescription();
        return "\u23f0 " + reminder.getTitle() + (description != null && !description.isBlank() ? "\n" + description : "");
    }

    private InlineKeyboardMarkup completedKeyboard(Reminder reminder) {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton("\u2705 Completed").callbackData("complete:" + reminder.getId())
        );
    }

    private void processRecurrence(Reminder reminder) {
        Recurrence recurrence = reminder.getRecurrence();
        if (recurrence == null || recurrence == Recurrence.ONCE) {
            reminder.setActive(false);
            // Keep validation happy on completed reminders by resetting the timestamp just ahead of now.
            reminder.setStartTime(LocalDateTime.now().plusSeconds(1));
            return;
        }
        switch (recurrence) {
            case DAILY -> reminder.setStartTime(reminder.getStartTime().plusDays(1));
            case WEEKLY -> reminder.setStartTime(reminder.getStartTime().plusWeeks(1));
            case MONTHLY -> reminder.setStartTime(reminder.getStartTime().plusMonths(1));
            default -> reminder.setActive(false);
        }
    }
}
