package com.tma.reminders.ui;

import java.util.LinkedHashMap;
import java.util.Map;

final class TelegramWebAppSupport {

    static final String DEFAULT_SPACING = "var(--lumo-space-m)";

    private TelegramWebAppSupport() {
    }

    static String backButtonEventData(boolean visible) {
        return "{\"is_visible\":" + visible + "}";
    }

    static String swipeBehaviorEventData(boolean allowVerticalSwipe) {
        return "{\"allow_vertical_swipe\":" + allowVerticalSwipe + "}";
    }

    static Map<String, String> safeAreaStyles() {
        Map<String, String> styles = new LinkedHashMap<>();
        styles.put("padding-top", "calc(" + DEFAULT_SPACING + " + var(--tg-safe-area-top, 0px))");
        styles.put("padding-bottom", "calc(" + DEFAULT_SPACING + " + var(--tg-safe-area-bottom, 0px))");
        styles.put("padding-left", "calc(" + DEFAULT_SPACING + " + var(--tg-safe-area-left, 0px))");
        styles.put("padding-right", "calc(" + DEFAULT_SPACING + " + var(--tg-safe-area-right, 0px))");
        styles.put("min-height", "var(--tg-viewport-height, 100vh)");
        return styles;
    }
}
