package com.tma.reminders.ui;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramWebAppSupportTest {

    @Test
    void backButtonEventDataReflectsVisibility() {
        assertThat(TelegramWebAppSupport.backButtonEventData(true))
                .isEqualTo("{\"is_visible\":true}");
        assertThat(TelegramWebAppSupport.backButtonEventData(false))
                .isEqualTo("{\"is_visible\":false}");
    }

    @Test
    void swipeBehaviorEventDataReflectsSwipePermission() {
        assertThat(TelegramWebAppSupport.swipeBehaviorEventData(true))
                .isEqualTo("{\"allow_vertical_swipe\":true}");
        assertThat(TelegramWebAppSupport.swipeBehaviorEventData(false))
                .isEqualTo("{\"allow_vertical_swipe\":false}");
    }

    @Test
    void safeAreaStylesIncludeViewportAndPaddingVariables() {
        Map<String, String> styles = TelegramWebAppSupport.safeAreaStyles();

        assertThat(styles)
                .containsEntry("min-height", "var(--tg-viewport-height, 100vh)")
                .containsEntry("padding-top",
                        "calc(" + TelegramWebAppSupport.DEFAULT_SPACING + " + var(--tg-safe-area-top, 0px))")
                .containsEntry("padding-bottom",
                        "calc(" + TelegramWebAppSupport.DEFAULT_SPACING + " + var(--tg-safe-area-bottom, 0px))")
                .containsEntry("padding-left",
                        "calc(" + TelegramWebAppSupport.DEFAULT_SPACING + " + var(--tg-safe-area-left, 0px))")
                .containsEntry("padding-right",
                        "calc(" + TelegramWebAppSupport.DEFAULT_SPACING + " + var(--tg-safe-area-right, 0px))");
    }
}
