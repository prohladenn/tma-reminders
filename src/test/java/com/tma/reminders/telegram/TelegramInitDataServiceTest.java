package com.tma.reminders.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tma.reminders.config.TelegramBotProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TelegramInitDataServiceTest {

    private static final String VALID_TEST_INIT_DATA =
            "user=%7B%22id%22%3A5000131393%2C%22first_name%22%3A%22Valeron%22%2C%22last_name%22%3A%22Fullstack%22%2C%22language_code%22%3A%22en%22%2C%22allows_write_to_pm%22%3Atrue%2C%22photo_url%22%3A%22https%3A%5C%2F%5C%2Fa-ttgme.stel.com%5C%2Fi%5C%2Fuserpic%5C%2F320%5C%2FF6a3dpP2QvqNAwj1JhjQk5MNHa1s0aItZKUwXglcceatQ36jkQqfYpuWLcFAc6UH.svg%22%7D&chat_instance=8307270906564316437&chat_type=sender&auth_date=1766321150&signature=3kag_ZJuyV3neFvSF3rA_RmwX0NvKvsfOR3CBSgYsL9h_PcBSo-1COdqo4uOtJQ_qgLs6Szi26_iFrq8O84HDw&hash=4d5d5c8964a414872552b72ac2308f023ab6ce96edcd42f982ca511d3872665c";

    private static final String VALID_PROD_INIT_DATA =
            "user=%7B%22id%22%3A330178816%2C%22first_name%22%3A%22Valeriy%22%2C%22last_name%22%3A%22Kovshov%22%2C%22username%22%3A%22prohladenn%22%2C%22language_code%22%3A%22en%22%2C%22is_premium%22%3Atrue%2C%22allows_write_to_pm%22%3Atrue%2C%22photo_url%22%3A%22https%3A%5C%2F%5C%2Ft.me%5C%2Fi%5C%2Fuserpic%5C%2F320%5C%2FkS8uFVyrSEle0RY8OozasQeZVEmXC2QDb9zMLfdznqc.svg%22%7D&chat_instance=-4627898068485394469&chat_type=sender&auth_date=1766321438&signature=94IIJvUVixIlxJWdhCyBIMVRMV-Zql8IAcEBqSt0-IU5TlHRKTBRbexDApX2Mgj7tuHgquERSBITC8PA72DrDA&hash=e4d8fd4d82bfb60cf8a42f1f9c32bb9f9aede01821f3d1eb72d90f8ee3d12e19";

    @Test
    void validatesTestEnvironmentInitData() {
        String token = requireToken("TELEGRAM_BOT_TOKEN_TEST");
        TelegramInitDataService service = new TelegramInitDataService(
                new TelegramBotProperties(token, "prod-placeholder", "test"),
                new ObjectMapper()
        );

        Optional<Long> result = service.validateAndExtractChatId(VALID_TEST_INIT_DATA);

        assertTrue(result.isPresent(), "Init data should be accepted in test environment");
        assertEquals(5000131393L, result.get());
    }

    @Test
    void validatesProdEnvironmentInitData() {
        String token = requireToken("TELEGRAM_BOT_TOKEN_PROD");
        TelegramInitDataService service = new TelegramInitDataService(
                new TelegramBotProperties("test-placeholder", token, "prod"),
                new ObjectMapper()
        );

        Optional<Long> result = service.validateAndExtractChatId(VALID_PROD_INIT_DATA);

        assertTrue(result.isPresent(), "Init data should be accepted in prod environment");
        assertEquals(330178816L, result.get());
    }

    private String requireToken(String envVar) {
        String token = Optional.ofNullable(System.getenv(envVar)).orElse("").trim();
        assumeTrue(!token.isBlank(), () -> envVar + " must be provided to run this test");
        return token;
    }
}
