package com.tma.reminders.telegram;

import com.tma.reminders.config.TelegramBotProperties;
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

    private static final Duration MAX_LOGIN_AGE = Duration.ofMinutes(10);

    private final TelegramBotProperties properties;

    public TelegramLoginService(TelegramBotProperties properties) {
        this.properties = properties;
    }

    public Optional<Long> validateAndExtractChatId(Map<String, String> loginData) {
        if (loginData == null || loginData.isEmpty()) {
            return Optional.empty();
        }

        String hash = loginData.get("hash");
        String authDate = loginData.get("auth_date");
        String id = loginData.get("id");

        if (hash == null || authDate == null || id == null) {
            return Optional.empty();
        }

        if (!isRecent(authDate)) {
            return Optional.empty();
        }

        if (!hash.equalsIgnoreCase(computeHash(loginData))) {
            return Optional.empty();
        }

        try {
            return Optional.of(Long.valueOf(id));
        } catch (NumberFormatException ex) {
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
