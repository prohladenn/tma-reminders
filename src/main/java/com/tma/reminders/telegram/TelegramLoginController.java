package com.tma.reminders.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TelegramLoginController.class);

    private final TelegramLoginService telegramLoginService;

    public TelegramLoginController(TelegramLoginService telegramLoginService) {
        this.telegramLoginService = telegramLoginService;
    }

    @PostMapping
    public ResponseEntity<LoginResponse> login(@RequestBody Map<String, Object> payload) {
        Map<String, String> params = payload.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue() == null ? "" : entry.getValue().toString()));

        log.debug("Received Telegram login payload with keys {}", params.keySet());

        return telegramLoginService.validateAndExtractChatId(params)
                .map(chatId -> {
                    log.debug("Telegram login successful for chatId {}", chatId);
                    return ResponseEntity.ok(new LoginResponse(String.valueOf(chatId)));
                })
                .orElseGet(() -> {
                    log.debug("Telegram login failed validation");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                });
    }

    @PostMapping("/tma")
    public ResponseEntity<LoginResponse> tmaLogin(@org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || authorization.isBlank()) {
            log.debug("TMA login missing Authorization header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String[] parts = authorization.split(" ", 2);
        if (parts.length != 2 || !"tma".equalsIgnoreCase(parts[0])) {
            log.debug("TMA login has unsupported auth type: {}", authorization);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String initData = parts[1];
        return telegramLoginService.validateTmaInitData(initData)
                .map(chatId -> {
                    log.debug("TMA login successful for chatId {}", chatId);
                    return ResponseEntity.ok(new LoginResponse(String.valueOf(chatId)));
                })
                .orElseGet(() -> {
                    log.debug("TMA login failed validation");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                });
    }

    public record LoginResponse(String chatId) {
    }
}
