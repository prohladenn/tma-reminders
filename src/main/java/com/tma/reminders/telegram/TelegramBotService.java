package com.tma.reminders.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.EditMessageReplyMarkup;
import com.pengrad.telegrambot.request.EditMessageText;
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
        this.bot = new TelegramBot.Builder(properties.token())
                .apiUrl("https://api.telegram.org/bot")
                .useTestServer(!properties.prod())
                .build();
    }

    public SendResult sendMessage(Long chatId, String text) {
        return sendMessage(chatId, text, null);
    }

    public SendResult sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        if (chatId == null) {
            return SendResult.error(null, "Chat ID is not configured");
        }
        var request = new SendMessage(chatId, text);
        if (keyboard != null) {
            request.replyMarkup(keyboard);
        }
        var response = bot.execute(request);
        if (!response.isOk()) {
            log.error("Failed to send Telegram message to chat {} (errorCode={}, description={})",
                    chatId, response.errorCode(), response.description());
            return SendResult.error(response.errorCode(), response.description());
        }
        Integer messageId = response.message() != null ? response.message().messageId() : null;
        return SendResult.ok(messageId);
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

    public void removeKeyboard(Long chatId, Integer messageId) {
        var response = bot.execute(new EditMessageReplyMarkup(chatId, messageId).replyMarkup(new InlineKeyboardMarkup()));
        if (!response.isOk()) {
            log.warn("Failed to remove inline keyboard for chat {} message {} (errorCode={}, description={})",
                    chatId, messageId, response.errorCode(), response.description());
        }
    }

    public void editMessage(Long chatId, Integer messageId, String text) {
        var response = bot.execute(new EditMessageText(chatId, messageId, text).replyMarkup(new InlineKeyboardMarkup()));
        if (!response.isOk()) {
            log.warn("Failed to edit message for chat {} message {} (errorCode={}, description={})",
                    chatId, messageId, response.errorCode(), response.description());
        }
    }

    public void deleteMessage(Long chatId, Integer messageId) {
        var response = bot.execute(new DeleteMessage(chatId, messageId));
        if (!response.isOk()) {
            log.warn("Failed to delete message for chat {} message {} (errorCode={}, description={})",
                    chatId, messageId, response.errorCode(), response.description());
        }
    }

    public void answerCallback(String callbackQueryId, String text) {
        var response = bot.execute(new AnswerCallbackQuery(callbackQueryId).text(text));
        if (!response.isOk()) {
            log.warn("Failed to answer callback query {} (errorCode={}, description={})",
                    callbackQueryId, response.errorCode(), response.description());
        }
    }

    public record SendResult(boolean success, Integer errorCode, String description, Integer messageId) {
        public static SendResult ok(Integer messageId) {
            return new SendResult(true, null, null, messageId);
        }

        public static SendResult error(Integer errorCode, String description) {
            return new SendResult(false, errorCode, description, null);
        }

        public boolean isNotFound() {
            return errorCode != null && errorCode == 404;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
