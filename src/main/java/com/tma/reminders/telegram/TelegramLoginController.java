package com.tma.reminders.telegram;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/telegram/login")
public class TelegramLoginController {

    private final TelegramLoginService telegramLoginService;

    public TelegramLoginController(TelegramLoginService telegramLoginService) {
        this.telegramLoginService = telegramLoginService;
    }

    @PostMapping
    public ResponseEntity<LoginResponse> login(@RequestBody Map<String, Object> payload) {
        Map<String, String> params = payload.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue() == null ? "" : entry.getValue().toString()));

        return telegramLoginService.validateAndExtractChatId(params)
                .map(chatId -> ResponseEntity.ok(new LoginResponse(String.valueOf(chatId))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    public record LoginResponse(String chatId) {
    }
}
