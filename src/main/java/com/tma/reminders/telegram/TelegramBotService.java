package com.tma.reminders.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.SendMessage;
import com.tma.reminders.config.TelegramBotProperties;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TelegramBotService {

    private final TelegramBot bot;
    private int lastUpdateId = 0;

    public TelegramBotService(TelegramBotProperties properties) {
        this.bot = new TelegramBot(properties.token());
    }

    public void sendMessage(Long chatId, String text) {
        bot.execute(new SendMessage(chatId, text));
    }

    public List<Update> pollUpdates(int limit) {
        var request = new GetUpdates()
                .limit(limit)
                .offset(lastUpdateId + 1)
                .timeout(0);
        var response = bot.execute(request);
        List<Update> updates = response.updates();
        if (updates != null && !updates.isEmpty()) {
            lastUpdateId = updates.get(updates.size() - 1).updateId();
        }
        return updates;
    }
}
