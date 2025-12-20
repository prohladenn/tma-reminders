package com.tma.reminders.telegram;

import com.tma.reminders.config.TelegramBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class TelegramLoginService {

    private static final Logger log = LoggerFactory.getLogger(TelegramLoginService.class);
    private static final Duration MAX_LOGIN_AGE = Duration.ofMinutes(10);

    private final TelegramBotProperties properties;

    public TelegramLoginService(TelegramBotProperties properties) {
        this.properties = properties;
    }

    public Optional<Long> validateAndExtractChatId(Map<String, String> loginData) {
        if (loginData == null || loginData.isEmpty()) {
            log.debug("Telegram login payload is empty");
            return Optional.empty();
        }

        String hash = loginData.get("hash");
        String authDate = loginData.get("auth_date");
        String id = loginData.get("id");

        if (hash == null || authDate == null || id == null) {
            log.debug("Telegram login payload missing required fields: hash={}, auth_date={}, id={}", hash != null, authDate, id);
            return Optional.empty();
        }

        if (!isRecent(authDate)) {
            log.debug("Telegram login payload rejected because auth_date {} is older than {} minutes", authDate, MAX_LOGIN_AGE.toMinutes());
            return Optional.empty();
        }

        String expectedHash = computeHash(loginData);
        if (!hash.equalsIgnoreCase(expectedHash)) {
            log.debug("Telegram login hash mismatch: provided={}, expected={}", hash, expectedHash);
            return Optional.empty();
        }

        try {
            Long chatId = Long.valueOf(id);
            log.debug("Telegram login validated for chatId {}", chatId);
            return Optional.of(chatId);
        } catch (NumberFormatException ex) {
            log.debug("Telegram login payload has invalid id: {}", id, ex);
            return Optional.empty();
        }
    }

    private boolean isRecent(String authDate) {
        try {
            Instant timestamp = Instant.ofEpochSecond(Long.parseLong(authDate));
            return timestamp.isAfter(Instant.now().minus(MAX_LOGIN_AGE));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String computeHash(Map<String, String> loginData) {
        TreeMap<String, String> sorted = new TreeMap<>(loginData);
        sorted.remove("hash");

        String dataCheckString = sorted.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        byte[] secretKey = sha256(properties.token());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            return bytesToHex(mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute Telegram login hash", ex);
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String part = Integer.toHexString(b & 0xFF);
            if (part.length() == 1) {
                hex.append('0');
            }
            hex.append(part);
        }
        return hex.toString();
    }
}
