package com.tma.reminders.reminder;

import com.tma.reminders.telegram.TelegramBotService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReminderService {

    private final ReminderRepository repository;
    private final TelegramBotService telegramBotService;

    public ReminderService(ReminderRepository repository, TelegramBotService telegramBotService) {
        this.repository = repository;
        this.telegramBotService = telegramBotService;
    }

    public Reminder save(Reminder reminder) {
        if (reminder.getNextFireAt() == null) {
            reminder.setNextFireAt(reminder.getStartTime());
        }
        return repository.save(reminder);
    }

    public List<Reminder> findAllByChatId(String chatId) {
        return repository.findAllByChatIdOrderByNextFireAtAsc(chatId);
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
            telegramBotService.sendMessage(Long.valueOf(reminder.getChatId()), formatMessage(reminder));
            processRecurrence(reminder);
        }
    }

    private String formatMessage(Reminder reminder) {
        String description = reminder.getDescription();
        return "\u23f0 " + reminder.getTitle() + (description != null && !description.isBlank() ? "\n" + description : "");
    }

    private void processRecurrence(Reminder reminder) {
        reminder.setLastSentAt(LocalDateTime.now());

        Recurrence recurrence = reminder.getRecurrence();
        if (recurrence == null || recurrence == Recurrence.ONCE) {
            reminder.setActive(false);
            reminder.setNextFireAt(null);
            return;
        }
        LocalDateTime nextFireAt = reminder.getNextFireAt();
        if (nextFireAt == null) {
            nextFireAt = LocalDateTime.now();
        }
        switch (recurrence) {
            case DAILY -> reminder.setNextFireAt(nextFireAt.plusDays(1));
            case WEEKLY -> reminder.setNextFireAt(nextFireAt.plusWeeks(1));
            case MONTHLY -> reminder.setNextFireAt(nextFireAt.plusMonths(1));
            default -> reminder.setActive(false);
        }
    }
}
