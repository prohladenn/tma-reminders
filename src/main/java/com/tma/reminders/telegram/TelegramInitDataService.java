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
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class TelegramInitDataService {

    private static final Logger log = LoggerFactory.getLogger(TelegramInitDataService.class);

    private static final String PROD_PUBLIC_KEY_HEX = "e7bf03a2fa4602af4580703d88dda5bb59f32ed8b02a56c187fe7d34caed242d";
    private static final String TEST_PUBLIC_KEY_HEX = "40055058a4ee38156a06562e52eece92a771bcd8346a8c4615cb7376eddf72ec";
    private static final Duration MAX_INIT_DATA_AGE = Duration.ofHours(1);

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

        if (!verifySignature(initData) && !verifyHash(initData)) {
            log.debug("Init data failed both signature and hash verification");
            return Optional.empty();
        }

        return extractUserId(initData.get("user"));
    }

    private boolean hasRequiredFields(Map<String, String> initData) {
        boolean hasUser = initData.containsKey("user");
        boolean hasAuthDate = initData.containsKey("auth_date");
        boolean hasHash = initData.containsKey("hash");
        boolean hasSignature = initData.containsKey("signature");
        if (!hasUser || !hasAuthDate || (!hasHash && !hasSignature)) {
            log.debug("Init data missing required fields: user={}, auth_date={}, hash={}, signature={}",
                    hasUser, hasAuthDate, hasHash, hasSignature);
            return false;
        }
        return true;
    }

    private boolean verifySignature(Map<String, String> initData) {
        String signatureValue = initData.get("signature");
        if (signatureValue == null || signatureValue.isBlank()) {
            return false;
        }
        try {
            String dataCheckString = buildSignaturePayload(initData);
            byte[] signatureBytes = decodeBase64(signatureValue);
            PublicKey publicKey = buildPublicKey(properties.test() ? TEST_PUBLIC_KEY_HEX : PROD_PUBLIC_KEY_HEX);

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(dataCheckString.getBytes(StandardCharsets.UTF_8));
            boolean verified = verifier.verify(signatureBytes);
            if (!verified) {
                log.debug("Init data Ed25519 signature verification failed");
            }
            return verified;
        } catch (Exception ex) {
            log.debug("Failed to verify init data signature", ex);
            return false;
        }
    }

    private boolean verifyHash(Map<String, String> initData) {
        String providedHash = initData.get("hash");
        if (providedHash == null || providedHash.isBlank()) {
            return false;
        }
        byte[] secret = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
                properties.token().getBytes(StandardCharsets.UTF_8));

        String dataCheckString = buildDataCheckString(initData, false);
        String expectedHash = bytesToHex(hmacSha256(secret, dataCheckString.getBytes(StandardCharsets.UTF_8)));
        if (expectedHash.equalsIgnoreCase(providedHash)) {
            return true;
        }

        String dataCheckWithSignature = buildDataCheckString(initData, true);
        String expectedWithSignature = bytesToHex(hmacSha256(secret, dataCheckWithSignature.getBytes(StandardCharsets.UTF_8)));
        if (expectedWithSignature.equalsIgnoreCase(providedHash)) {
            return true;
        }

        log.debug("Init data hash mismatch: provided={}, expectedWithoutSignature={}, expectedWithSignature={}",
                providedHash, expectedHash, expectedWithSignature);
        return false;
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

    private boolean isRecent(String authDate) {
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

    private String buildDataCheckString(Map<String, String> initData, boolean includeSignature) {
        return initData.entrySet().stream()
                .filter(entry -> !"hash".equals(entry.getKey()))
                .filter(entry -> includeSignature || !"signature".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private String buildSignaturePayload(Map<String, String> initData) {
        String botId = extractBotId(properties.token());
        String dataCheckString = buildDataCheckString(initData, false);
        return botId + ":WebAppData\n" + dataCheckString;
    }

    private String extractBotId(String token) {
        int idx = token.indexOf(':');
        if (idx > 0) {
            return token.substring(0, idx);
        }
        return token;
    }

    private PublicKey buildPublicKey(String publicKeyHex) {
        try {
            byte[] rawKey = hexToBytes(publicKeyHex);
            byte[] prefix = hexToBytes("302a300506032b6570032100");
            byte[] encoded = new byte[prefix.length + rawKey.length];
            System.arraycopy(prefix, 0, encoded, 0, prefix.length);
            System.arraycopy(rawKey, 0, encoded, prefix.length, rawKey.length);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            return KeyFactory.getInstance("Ed25519").generatePublic(keySpec);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build Ed25519 public key", ex);
        }
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

    private byte[] decodeBase64(String value) {
        String padded = padBase64(value);
        return Base64.getUrlDecoder().decode(padded);
    }

    private String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }

    private byte[] hexToBytes(String hex) {
        int length = hex.length();
        byte[] bytes = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
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
