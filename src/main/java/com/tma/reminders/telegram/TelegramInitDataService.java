package com.tma.reminders.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
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
import java.util.stream.Collectors;

@Service
public class TelegramInitDataService {

    private static final Logger log = LoggerFactory.getLogger(TelegramInitDataService.class);

    private static final Duration MAX_INIT_DATA_AGE = Duration.ofHours(2);

    private final TelegramBotProperties properties;
    private final ObjectMapper objectMapper;

    public TelegramInitDataService(TelegramBotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<Long> validateAndExtractChatId(String initDataRaw) {
        if (initDataRaw == null || initDataRaw.isBlank()) {
            log.debug("Init data payload is empty");
            return Optional.empty();
        }
        Map<String, String> initData = parseInitData(initDataRaw);
        if (initData.isEmpty()) {
            log.debug("Init data payload has no parameters");
            return Optional.empty();
        }

        if (!hasRequiredFields(initData)) {
            return Optional.empty();
        }
        if (!isRecent(initData.get("auth_date"))) {
            log.debug("Init data rejected because auth_date {} is older than {} minutes",
                    initData.get("auth_date"), MAX_INIT_DATA_AGE.toMinutes());
            return Optional.empty();
        }

        if (!verifyHash(initData)) {
            log.debug("Init data hash verification failed");
            return Optional.empty();
        }

        return extractUserId(initData.get("user"));
    }

    private boolean hasRequiredFields(Map<String, String> initData) {
        boolean hasUser = initData.containsKey("user");
        boolean hasAuthDate = initData.containsKey("auth_date");
        boolean hasHash = initData.containsKey("hash");
        if (!hasUser || !hasAuthDate || !hasHash) {
            log.debug("Init data missing required fields: user={}, auth_date={}, hash={}",
                    hasUser, hasAuthDate, hasHash);
            return false;
        }
        return true;
    }

    private boolean verifyHash(Map<String, String> initData) {
        String providedHash = initData.get("hash");
        if (providedHash == null || providedHash.isBlank()) {
            return false;
        }
        byte[] secret = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
                properties.token().getBytes(StandardCharsets.UTF_8));

        String dataCheckString = buildDataCheckString(initData);
        String expectedHash = bytesToHex(hmacSha256(secret, dataCheckString.getBytes(StandardCharsets.UTF_8)));
        boolean matches = expectedHash.equalsIgnoreCase(providedHash);
        if (!matches) {
            log.debug("Init data hash mismatch: provided={}, expected={}", providedHash, expectedHash);
        }
        return matches;
    }

    private Optional<Long> extractUserId(String userJson) {
        try {
            JsonNode userNode = objectMapper.readTree(userJson);
            JsonNode idNode = userNode.get("id");
            if (idNode == null || !idNode.canConvertToLong()) {
                log.debug("Init data user payload has no valid id: {}", userJson);
                return Optional.empty();
            }
            long chatId = idNode.asLong();
            log.debug("Init data validated for chatId {}", chatId);
            return Optional.of(chatId);
        } catch (Exception ex) {
            log.debug("Failed to parse init data user payload", ex);
            return Optional.empty();
        }
    }

    @VisibleForTesting
    boolean isRecent(String authDate) {
        try {
            Instant timestamp = Instant.ofEpochSecond(Long.parseLong(authDate));
            return timestamp.isAfter(Instant.now().minus(MAX_INIT_DATA_AGE));
        } catch (NumberFormatException ex) {
            log.debug("Init data contains non-numeric auth_date: {}", authDate);
            return false;
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

    private String buildDataCheckString(Map<String, String> initData) {
        return initData.entrySet().stream()
                .filter(entry -> !"hash".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", ex);
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
