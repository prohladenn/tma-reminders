package com.tma.reminders.reminder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    @Query("select r from Reminder r where r.active = true and coalesce(r.nextAttemptAt, r.startTime) <= :now")
    List<Reminder> findDueReminders(@Param("now") LocalDateTime now);

    List<Reminder> findAllByChatIdOrderByStartTimeAsc(String chatId);
}
