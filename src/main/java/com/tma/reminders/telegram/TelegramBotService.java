package com.tma.reminders.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.SendMessage;
import com.tma.reminders.config.TelegramBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TelegramBotService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private final TelegramBot bot;
    private int lastUpdateId = 0;

    public TelegramBotService(TelegramBotProperties properties) {
        this.bot = new TelegramBot(properties.token());
    }

    public SendResult sendMessage(Long chatId, String text) {
        if (chatId == null) {
            return SendResult.error(null, "Chat ID is not configured");
        }
        var response = bot.execute(new SendMessage(chatId, text));
        if (!response.isOk()) {
            log.error("Failed to send Telegram message to chat {} (errorCode={}, description={})",
                    chatId, response.errorCode(), response.description());
            return SendResult.error(response.errorCode(), response.description());
        }
        return SendResult.ok();
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

    public record SendResult(boolean success, Integer errorCode, String description) {
        public static SendResult ok() {
            return new SendResult(true, null, null);
        }

        public static SendResult error(Integer errorCode, String description) {
            return new SendResult(false, errorCode, description);
        }

        public boolean isNotFound() {
            return errorCode != null && errorCode == 404;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
