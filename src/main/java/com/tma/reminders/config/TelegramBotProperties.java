package com.tma.reminders.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "telegram.bot")
@Validated
public record TelegramBotProperties(
        @NotBlank(message = "Telegram test bot token must be provided")
        String tokenTest,
        @NotBlank(message = "Telegram prod bot token must be provided")
        String tokenProd,
        @NotBlank(message = "Telegram bot environment must be provided (test|prod)")
        String environment) {

    public String token() {
        return prod() ? tokenProd : tokenTest;
    }

    public boolean prod() {
        return "prod".equalsIgnoreCase(environment);
    }
}
