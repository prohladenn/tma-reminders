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
            telegramBotService.sendMessage(Long.valueOf(reminder.getChatId()), formatMessage(reminder));
            processRecurrence(reminder);
        }
    }

    private String formatMessage(Reminder reminder) {
        String description = reminder.getDescription();
        return "\u23f0 " + reminder.getTitle() + (description != null && !description.isBlank() ? "\n" + description : "");
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
