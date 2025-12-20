package com.tma.reminders.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tma.reminders.config.TelegramBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class TelegramLoginService {

    private static final Logger log = LoggerFactory.getLogger(TelegramLoginService.class);
    private static final Duration MAX_TMA_AGE = Duration.ofHours(1);

    private final TelegramBotProperties properties;
    private final ObjectMapper objectMapper;

    public TelegramLoginService(TelegramBotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<Long> validateWebAppData(String tgWebAppData) {
        if (tgWebAppData == null || tgWebAppData.isBlank()) {
            log.debug("TMA init data is empty");
            return Optional.empty();
        }
        String decoded = URLDecoder.decode(tgWebAppData, StandardCharsets.UTF_8);
        Map<String, String> initData = parseInitData(decoded);
        String hash = initData.get("hash");
        String authDate = initData.get("auth_date");
        if (hash == null || authDate == null) {
            log.debug("TMA init data missing required fields: hash={}, auth_date={}", hash != null, authDate);
            return Optional.empty();
        }
        if (!isRecent(authDate, MAX_TMA_AGE)) {
            log.debug("TMA init data rejected because auth_date {} is older than {} minutes", authDate, MAX_TMA_AGE.toMinutes());
            return Optional.empty();
        }
        String expectedHash = computeWebAppHash(initData);
        if (!hash.equalsIgnoreCase(expectedHash)) {
            log.debug("TMA init data hash mismatch: provided={}, expected={}", hash, expectedHash);
            return Optional.empty();
        }
        String userJson = initData.get("user");
        if (userJson == null) {
            log.debug("TMA init data does not contain user payload");
            return Optional.empty();
        }
        try {
            JsonNode userNode = objectMapper.readTree(userJson);
            JsonNode idNode = userNode.get("id");
            if (idNode == null || !idNode.canConvertToLong()) {
                log.debug("TMA user payload has no valid id: {}", userJson);
                return Optional.empty();
            }
            long chatId = idNode.asLong();
            log.debug("TMA init data validated for chatId {}", chatId);
            return Optional.of(chatId);
        } catch (Exception ex) {
            log.debug("Failed to parse TMA user payload", ex);
            return Optional.empty();
        }
    }

    private boolean isRecent(String authDate, Duration maxAge) {
        try {
            Instant timestamp = Instant.ofEpochSecond(Long.parseLong(authDate));
            return timestamp.isAfter(Instant.now().minus(maxAge));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String computeWebAppHash(Map<String, String> loginData) {
        TreeMap<String, String> sorted = new TreeMap<>(loginData);
        sorted.remove("hash");

        String dataCheckString = sorted.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        byte[] secretKey = hmacSha256("WebAppData", properties.token().getBytes(StandardCharsets.UTF_8));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            return bytesToHex(mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute Telegram WebApp hash", ex);
        }
    }

    private Map<String, String> parseInitData(String initDataRaw) {
        TreeMap<String, String> result = new TreeMap<>();
        for (String part : initDataRaw.split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private byte[] hmacSha256(String key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute secret key hash", ex);
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
