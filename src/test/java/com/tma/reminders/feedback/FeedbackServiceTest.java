package com.tma.reminders.feedback;

import com.tma.reminders.user.UserSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    private FeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(feedbackRepository);
    }

    @Test
    void savesFeedbackWithUserContext() {
        UserSettings settings = new UserSettings();
        settings.setChatId("999");
        settings.setTimeZoneId("UTC");

        doAnswer(invocation -> invocation.getArgument(0)).when(feedbackRepository).save(any(Feedback.class));

        Feedback saved = feedbackService.saveFeedback("Love the app", settings, Locale.forLanguageTag("ru-RU"));

        assertThat(saved).isNotNull();
        assertThat(saved.getMessage()).isEqualTo("Love the app");
        assertThat(saved.getChatId()).isEqualTo("999");
        assertThat(saved.getTimeZoneId()).isEqualTo("UTC");
        assertThat(saved.getLocale()).isEqualTo("ru-RU");
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
