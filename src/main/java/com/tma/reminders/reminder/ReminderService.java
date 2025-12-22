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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final int MAX_RESENDS = 2;
    private static final int MAX_DELIVERY_ATTEMPTS = MAX_RESENDS + 1;
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(2);

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
        reminder.setNextAttemptAt(reminder.getStartTime());
        reminder.setSendAttempts(0);
        reminder.setLastSentAt(null);
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
            normalizeNextAttempt(reminder);
            SendResult result = telegramBotService.sendMessage(Long.valueOf(reminder.getChatId()), formatMessage(reminder, false),
                    completedKeyboard(reminder));
            if (result.isSuccess()) {
                handleSuccessfulSend(reminder, now, result);
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
    public CompletionResult completeReminder(Long reminderId, String chatId) {
        return repository.findById(reminderId)
                .filter(reminder -> reminder.getChatId().equals(chatId))
                .map(reminder -> {
                    Integer messageId = reminder.getLastSentMessageId();
                    String completedText = formatMessage(reminder, true);
                    scheduleNextOccurrence(reminder);
                    return new CompletionResult(true, messageId, completedText);
                })
                .orElse(new CompletionResult(false, null, null));
    }

    private void handleSuccessfulSend(Reminder reminder, LocalDateTime now, SendResult result) {
        Integer previousMessageId = reminder.getLastSentMessageId();
        reminder.setLastSentAt(now);
        reminder.setSendAttempts(reminder.getSendAttempts() + 1);
        reminder.setLastSentMessageId(result.messageId());

        if (reminder.getSendAttempts() >= MAX_DELIVERY_ATTEMPTS) {
            scheduleNextOccurrence(reminder);
        } else {
            reminder.setNextAttemptAt(now.plus(RESEND_INTERVAL));
        }

        if (previousMessageId != null && result.messageId() != null && !previousMessageId.equals(result.messageId())) {
            telegramBotService.deleteMessage(Long.valueOf(reminder.getChatId()), previousMessageId);
        }
    }

    private void normalizeNextAttempt(Reminder reminder) {
        if (reminder.getNextAttemptAt() == null) {
            reminder.setNextAttemptAt(reminder.getStartTime());
        }
    }

    private String formatMessage(Reminder reminder, boolean completed) {
        String description = reminder.getDescription();
        String prefix = completed ? "\u2705 " : "\u23f0 ";
        return prefix + reminder.getTitle() + (description != null && !description.isBlank() ? "\n" + description : "");
    }

    private InlineKeyboardMarkup completedKeyboard(Reminder reminder) {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton("\u2705 Completed").callbackData("complete:" + reminder.getId())
        );
    }

    private void scheduleNextOccurrence(Reminder reminder) {
        Recurrence recurrence = reminder.getRecurrence();
        if (recurrence == null || recurrence == Recurrence.ONCE) {
            reminder.setActive(false);
            // Keep validation happy on completed reminders by resetting the timestamp just ahead of now.
            reminder.setStartTime(LocalDateTime.now().plusSeconds(1));
        } else {
            switch (recurrence) {
                case DAILY -> reminder.setStartTime(reminder.getStartTime().plusDays(1));
                case WEEKLY -> reminder.setStartTime(reminder.getStartTime().plusWeeks(1));
                case MONTHLY -> reminder.setStartTime(reminder.getStartTime().plusMonths(1));
                default -> reminder.setActive(false);
            }
        }
        reminder.setNextAttemptAt(reminder.getStartTime());
        reminder.setLastSentAt(null);
        reminder.setSendAttempts(0);
    }

    public record CompletionResult(boolean completed, Integer messageId, String updatedText) {
    }
}
