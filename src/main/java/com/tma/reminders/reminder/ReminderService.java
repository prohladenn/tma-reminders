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
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(2);
    private static final int DEFAULT_MAX_RETRY_COUNT = 2;

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
        reminder.setLastSentMessageId(null);
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
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Reminder> dueReminders = repository.findDueReminders(now);
        int maxDeliveryAttempts = resolveMaxDeliveryAttempts();
        for (Reminder reminder : dueReminders) {
            initializeNextAttemptAt(reminder);
            int attemptNumber = reminder.getSendAttempts() + 1;
            reminder.setSendAttempts(attemptNumber);
            Integer previousMessageId = reminder.getLastSentMessageId();
            boolean isRetry = attemptNumber > 1;
            SendResult result;
            try {
                result = telegramBotService.sendMessage(Long.valueOf(reminder.getChatId()), formatMessage(reminder, false),
                        completedKeyboard(reminder));
            } catch (Exception ex) {
                log.error("Failed to send reminder {} to chat {} because of an exception; scheduling retry.",
                        reminder.getId(), reminder.getChatId(), ex);
                handleFailedSend(reminder, now, ex.getMessage());
                continue;
            }
            if (result.isSuccess()) {
                handleSuccessfulSend(reminder, now, result, previousMessageId, isRetry, maxDeliveryAttempts);
            } else if (result.isNotFound()) {
                reminder.setActive(false);
                reminder.setNextAttemptAt(null);
                log.warn("Disabling reminder {} for chat {} because Telegram returned 404 ({}). Check that the bot is started and chat id is correct.",
                        reminder.getId(), reminder.getChatId(), result.description());
            } else {
                handleFailedSend(reminder, now, result.description(), maxDeliveryAttempts);
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
                    handleCompletion(reminder);
                    return new CompletionResult(true, messageId, completedText);
                })
                .orElse(new CompletionResult(false, null, null));
    }

    private void handleSuccessfulSend(Reminder reminder, LocalDateTime now, SendResult result,
                                      Integer previousMessageId, boolean isRetry, int maxDeliveryAttempts) {
        reminder.setLastSentAt(now);
        reminder.setLastSentMessageId(result.messageId());

        if (isRetry && previousMessageId != null && result.messageId() != null && !previousMessageId.equals(result.messageId())) {
            telegramBotService.deleteMessage(Long.valueOf(reminder.getChatId()), previousMessageId);
        }

        if (hasUsedAllAttempts(reminder, maxDeliveryAttempts)) {
            scheduleAfterFinalAttempt(reminder);
        } else {
            reminder.setNextAttemptAt(now.plus(RESEND_INTERVAL));
        }
    }

    private void handleFailedSend(Reminder reminder, LocalDateTime now, String description, int maxDeliveryAttempts) {
        if (hasUsedAllAttempts(reminder, maxDeliveryAttempts)) {
            scheduleAfterFinalAttempt(reminder);
            log.warn("Reminder {} reached max attempts after failure ({}); moving forward.", reminder.getId(), description);
        } else {
            reminder.setNextAttemptAt(now.plus(RESEND_INTERVAL));
            String descriptionWithRetryInfo = appendRetryInfo(description, reminder.getSendAttempts() + 1, maxDeliveryAttempts);
            log.warn("Reminder {} will retry after failure ({}). Next attempt at {} UTC.", reminder.getId(), descriptionWithRetryInfo, reminder.getNextAttemptAt());
        }
    }

    private void initializeNextAttemptAt(Reminder reminder) {
        if (reminder.getNextAttemptAt() == null) {
            if (reminder.getStartTime() == null) {
                reminder.setStartTime(LocalDateTime.now(ZoneOffset.UTC));
            }
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

    public record CompletionResult(boolean completed, Integer messageId, String updatedText) {
    }

    private String appendRetryInfo(String description, int retryNumber, int maxDeliveryAttempts) {
        String baseDescription = description == null ? "null" : description;
        return "%s (retry %d of %d)".formatted(baseDescription, retryNumber, maxDeliveryAttempts);
    }

    private boolean hasUsedAllAttempts(Reminder reminder, int maxDeliveryAttempts) {
        return reminder.getSendAttempts() >= maxDeliveryAttempts;
    }

    private void scheduleAfterFinalAttempt(Reminder reminder) {
        Recurrence recurrence = reminder.getRecurrence();
        if (recurrence == null || recurrence == Recurrence.ONCE) {
            reminder.setActive(false);
            reminder.setNextAttemptAt(null);
            return;
        }

        reminder.setStartTime(calculateNextStartTime(reminder));
        reminder.setNextAttemptAt(reminder.getStartTime());
        reminder.setSendAttempts(0);
        reminder.setLastSentAt(null);
        reminder.setLastSentMessageId(null);
    }

    private void handleCompletion(Reminder reminder) {
        if (reminder.getRecurrence() == null || reminder.getRecurrence() == Recurrence.ONCE) {
            reminder.setActive(false);
            reminder.setNextAttemptAt(null);
        } else {
            reminder.setStartTime(calculateNextStartTime(reminder));
            reminder.setNextAttemptAt(reminder.getStartTime());
            reminder.setSendAttempts(0);
            reminder.setLastSentAt(null);
            reminder.setLastSentMessageId(null);
        }
    }

    private LocalDateTime calculateNextStartTime(Reminder reminder) {
        LocalDateTime baseTime = reminder.getStartTime();
        if (baseTime == null) {
            baseTime = LocalDateTime.now(ZoneOffset.UTC);
        }
        return switch (reminder.getRecurrence()) {
            case DAILY -> baseTime.plusDays(1);
            case WEEKLY -> baseTime.plusWeeks(1);
            case MONTHLY -> baseTime.plusMonths(1);
            default -> baseTime;
        };
    }

    private int resolveMaxDeliveryAttempts() {
        Integer maxRetryCount = userSettingsService.getSettings().getMaxRetryCount();
        int retries = maxRetryCount == null ? DEFAULT_MAX_RETRY_COUNT : Math.max(0, maxRetryCount);
        return retries + 1;
    }
}
