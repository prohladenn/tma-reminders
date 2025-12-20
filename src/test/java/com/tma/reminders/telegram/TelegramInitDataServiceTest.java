package com.tma.reminders.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tma.reminders.config.TelegramBotProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramInitDataServiceTest {

    private static final String USER_JSON = """
            {"id":5000131393,"first_name":"Valeron","last_name":"Fullstack","language_code":"en","allows_write_to_pm":true,"photo_url":"https://a-ttgme.stel.com/i/userpic/320/F6a3dpP2QvqNAwj1JhjQk5MNHa1s0aItZKUwXglcceatQ36jkQqfYpuWLcFAc6UH.svg"}
            """;

    @Test
    void validatesAndExtractsChatIdForValidInitData() {
        TelegramBotProperties properties = new TelegramBotProperties("testbot", "7342037359:TEST_TOKEN", true);
        TelegramInitDataService service = new TelegramInitDataService(properties, new ObjectMapper());

        String initDataWithoutHash = "user=" + USER_JSON +
                "&chat_instance=8473522709835892796" +
                "&chat_type=sender" +
                "&auth_date=1766270758" +
                "&signature=yjzQ5fNY_aspT0YsiqfcHmruUUJSPj58KdCqDV1yPpsx1rSiUZwuVNYPkY8cbmSaigxQJL-oTgAGrXgBQk7NDA";

        String hash = computeHash(initDataWithoutHash, properties.token());
        String initData = initDataWithoutHash + "&hash=" + hash;

        Optional<Long> result = service.validateAndExtractChatId(initData);

        assertTrue(result.isPresent(), "Init data should be accepted");
        assertEquals(5000131393L, result.get());
    }

    private String computeHash(String initData, String token) {
        Map<String, String> params = new TreeMap<>();
        for (String part : initData.split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            params.put(part.substring(0, idx), part.substring(idx + 1));
        }
        params.putIfAbsent("hash", "");

        String dataCheckString = params.entrySet().stream()
                .filter(entry -> !"hash".equals(entry.getKey()))
                .filter(entry -> !"signature".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        byte[] secret = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
        byte[] computed = hmacSha256(secret, dataCheckString.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(computed);
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
