package com.tma.reminders.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "telegram.bot")
@Validated
public record TelegramBotProperties(
        @NotBlank(message = "Telegram bot username must be provided")
        String username,
        @NotBlank(message = "Telegram bot token must be provided")
        String token,
        @NotNull(message = "Telegram bot test environment flag must be provided")
        Boolean test) {
}
