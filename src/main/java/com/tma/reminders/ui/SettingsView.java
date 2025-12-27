package com.tma.reminders.ui;

import com.tma.reminders.feedback.FeedbackService;
import com.tma.reminders.i18n.MessageService;
import com.tma.reminders.telegram.TelegramBotService;
import com.tma.reminders.telegram.TelegramInitDataService;
import com.tma.reminders.user.UserSettings;
import com.tma.reminders.user.UserSettingsService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.FlexLayout.FlexWrap;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Route("settings")
@PageTitle("Settings")
@PermitAll
public class SettingsView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(SettingsView.class);

    private final TelegramBotService telegramBotService;
    private final TelegramInitDataService telegramInitDataService;
    private final UserSettingsService userSettingsService;
    private final FeedbackService feedbackService;
    private final MessageService messageService;
    private final TextField chatIdField = new TextField();
    private final ComboBox<ZoneId> timeZoneField = new ComboBox<>();
    private final IntegerField maxRetryCountField = new IntegerField();
    private final TimePicker quietHoursStartField = new TimePicker();
    private final TimePicker quietHoursEndField = new TimePicker();
    private final ComboBox<Locale> localeField = new ComboBox<>();
    private final Button backButton = new Button(new Icon(VaadinIcon.ARROW_LEFT));
    private final Button testMessageButton = new Button();
    private final TextArea feedbackField = new TextArea();
    private final Button sendFeedbackButton = new Button();
    private final Div loadingOverlay = new Div();
    private final Paragraph loadingMessage = new Paragraph();
    private final ProgressBar loadingBar = new ProgressBar();
    private UserSettings currentSettings;
    private boolean applyingSettings;
    private H2 headerTitle;
    private H2 feedbackTitle;

    public SettingsView(TelegramBotService telegramBotService, TelegramInitDataService telegramInitDataService,
                        UserSettingsService userSettingsService, FeedbackService feedbackService, MessageService messageService) {
        this.telegramBotService = telegramBotService;
        this.telegramInitDataService = telegramInitDataService;
        this.userSettingsService = userSettingsService;
        this.feedbackService = feedbackService;
        this.messageService = messageService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.STRETCH);

        add(buildHeader(), buildSettingsForm(), buildLoadingOverlay());
        updateChatIdState();
        requestChatIdFromTelegram();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        attachEvent.getUI().getPage().setTitle(messageService.get(getUserLocale(), "section.settings"));
    }

    private HorizontalLayout buildHeader() {
        backButton.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate(MainView.class)));
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        backButton.getElement().setProperty("title", messageService.get(getUserLocale(), "button.back"));

        headerTitle = new H2();
        headerTitle.getStyle().set("margin", "0");

        HorizontalLayout header = new HorizontalLayout(backButton, headerTitle);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.START);
        header.getStyle().set("gap", "var(--lumo-space-s)");
        headerTitle.setText(messageService.get(getUserLocale(), "section.settings"));
        return header;
    }

    private VerticalLayout buildSettingsForm() {
        updateLabels();
        chatIdField.setPlaceholder(messageService.get(getUserLocale(), "placeholder.chatId"));
        chatIdField.setReadOnly(true);
        chatIdField.setWidthFull();
        userSettingsService.getChatId().ifPresent(chatIdField::setValue);

        currentSettings = userSettingsService.getSettings();

        timeZoneField.setItems(ZoneId.getAvailableZoneIds().stream()
                .sorted()
                .map(ZoneId::of)
                .toList());
        timeZoneField.setItemLabelGenerator(ZoneId::getId);
        timeZoneField.setWidthFull();

        maxRetryCountField.setMin(0);
        maxRetryCountField.setStepButtonsVisible(true);
        maxRetryCountField.setWidthFull();

        quietHoursStartField.setStep(Duration.ofMinutes(15));
        quietHoursEndField.setStep(Duration.ofMinutes(15));
        quietHoursStartField.setWidthFull();
        quietHoursEndField.setWidthFull();

        localeField.setItems(buildLocaleOptions(currentSettings));
        localeField.setItemLabelGenerator(locale -> locale.getDisplayName(locale));
        localeField.setWidthFull();

        applySettingsToFields(currentSettings);

        timeZoneField.addValueChangeListener(event -> {
            if (applyingSettings) {
                return;
            }
            ZoneId zoneId = event.getValue();
            currentSettings.setTimeZoneId(zoneId == null ? null : zoneId.getId());
            persistSettings();
        });
        maxRetryCountField.addValueChangeListener(event -> {
            if (applyingSettings) {
                return;
            }
            currentSettings.setMaxRetryCount(event.getValue());
            persistSettings();
        });
        quietHoursStartField.addValueChangeListener(event -> {
            if (applyingSettings) {
                return;
            }
            currentSettings.setQuietHoursStart(event.getValue());
            persistSettings();
        });
        quietHoursEndField.addValueChangeListener(event -> {
            if (applyingSettings) {
                return;
            }
            currentSettings.setQuietHoursEnd(event.getValue());
            persistSettings();
        });
        localeField.addValueChangeListener(event -> {
            if (applyingSettings) {
                return;
            }
            Locale locale = event.getValue();
            currentSettings.setLocale(locale == null ? null : locale.toLanguageTag());
            persistSettings();
        });

        testMessageButton.setText(messageService.get(getUserLocale(), "button.testMessage"));
        testMessageButton.addClickListener(event -> sendTestMessage());
        testMessageButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        testMessageButton.setMinWidth("150px");
        testMessageButton.setWidthFull();

        FlexLayout settingsLayout = new FlexLayout(chatIdField, timeZoneField, maxRetryCountField,
                quietHoursStartField, quietHoursEndField, localeField, testMessageButton);
        settingsLayout.setFlexWrap(FlexWrap.WRAP);
        settingsLayout.setWidthFull();
        settingsLayout.setAlignItems(Alignment.END);
        settingsLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        settingsLayout.setFlexGrow(1, chatIdField);
        settingsLayout.getStyle().set("gap", "var(--lumo-space-s)");

        VerticalLayout container = new VerticalLayout(settingsLayout, buildFeedbackForm());
        container.setPadding(false);
        container.setSpacing(true);
        container.setWidthFull();
        return container;
    }

    private VerticalLayout buildFeedbackForm() {
        feedbackTitle = new H2();
        feedbackTitle.setText(messageService.get(getUserLocale(), "section.feedback"));
        feedbackTitle.getStyle().set("margin", "var(--lumo-space-m) 0 0");

        feedbackField.setWidthFull();
        feedbackField.setMinHeight("140px");
        feedbackField.setMaxLength(2000);
        feedbackField.setClearButtonVisible(true);
        feedbackField.setLabel(messageService.get(getUserLocale(), "label.feedback"));

        sendFeedbackButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sendFeedbackButton.setMinWidth("150px");
        sendFeedbackButton.setText(messageService.get(getUserLocale(), "button.sendFeedback"));
        sendFeedbackButton.addClickListener(event -> submitFeedback());

        FlexLayout actions = new FlexLayout(sendFeedbackButton);
        actions.setJustifyContentMode(JustifyContentMode.START);
        actions.setWidthFull();

        VerticalLayout feedbackLayout = new VerticalLayout(feedbackTitle, feedbackField, actions);
        feedbackLayout.setPadding(false);
        feedbackLayout.setSpacing(true);
        feedbackLayout.setWidthFull();
        feedbackLayout.getStyle().set("gap", "var(--lumo-space-s)");
        return feedbackLayout;
    }

    private Div buildLoadingOverlay() {
        loadingOverlay.getStyle().set("position", "fixed");
        loadingOverlay.getStyle().set("inset", "0");
        loadingOverlay.getStyle().set("background", "var(--lumo-base-color)");
        loadingOverlay.getStyle().set("display", "flex");
        loadingOverlay.getStyle().set("flex-direction", "column");
        loadingOverlay.getStyle().set("align-items", "center");
        loadingOverlay.getStyle().set("justify-content", "center");
        loadingOverlay.getStyle().set("gap", "var(--lumo-space-m)");
        loadingOverlay.getStyle().set("z-index", "1000");

        loadingBar.setIndeterminate(true);
        loadingBar.setWidth("240px");
        loadingMessage.getStyle().set("margin", "0");
        loadingMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");
        loadingMessage.getStyle().set("text-align", "center");
        loadingMessage.getStyle().set("max-width", "360px");

        loadingOverlay.add(loadingBar, loadingMessage);
        loadingOverlay.setVisible(false);
        return loadingOverlay;
    }

    private void persistSettings() {
        currentSettings.setChatId(chatIdField.getValue());
        currentSettings = userSettingsService.updateSettings(currentSettings);
        withApplyingSettings(() -> {
            localeField.setItems(buildLocaleOptions(currentSettings));
            applySettingsToFields(currentSettings);
        });
        Notification.show(messageService.get(getUserLocale(), "notification.settingsSaved"),
                1500, Notification.Position.BOTTOM_CENTER);
    }

    private void applySettingsToFields(UserSettings settings) {
        withApplyingSettings(() -> {
            timeZoneField.setValue(getUserZoneId());
            maxRetryCountField.setValue(settings.getMaxRetryCount());
            quietHoursStartField.setValue(settings.getQuietHoursStart());
            quietHoursEndField.setValue(settings.getQuietHoursEnd());
            localeField.setValue(getUserLocale());
            updateLabels();
        });
    }

    private List<Locale> buildLocaleOptions(UserSettings settings) {
        Locale currentLocale = settings == null || settings.getLocale() == null || settings.getLocale().isBlank()
                ? Locale.getDefault()
                : Locale.forLanguageTag(settings.getLocale());
        Set<Locale> locales = new java.util.LinkedHashSet<>();
        locales.add(currentLocale);
        locales.add(Locale.getDefault());
        locales.add(Locale.ENGLISH);
        locales.add(Locale.forLanguageTag("ru-RU"));
        locales.add(Locale.forLanguageTag("en-US"));
        return locales.stream()
                .sorted(Comparator.comparing(Locale::toLanguageTag))
                .collect(Collectors.toList());
    }

    private ZoneId getUserZoneId() {
        if (currentSettings == null || currentSettings.getTimeZoneId() == null || currentSettings.getTimeZoneId().isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(currentSettings.getTimeZoneId());
        } catch (Exception ex) {
            return ZoneId.of("UTC");
        }
    }

    private Locale getUserLocale() {
        if (currentSettings == null || currentSettings.getLocale() == null || currentSettings.getLocale().isBlank()) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(currentSettings.getLocale());
    }

    private void sendTestMessage() {
        String chatId = userSettingsService.getChatId().orElse(null);
        if (chatId == null || chatId.isBlank()) {
            Notification.show(messageService.get(getUserLocale(), "notification.chatIdMissing"),
                    3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        Long parsedId;
        try {
            parsedId = Long.valueOf(chatId);
        } catch (NumberFormatException ex) {
            Notification.show(messageService.get(getUserLocale(), "notification.chatIdInvalid"),
                    3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        var result = telegramBotService.sendMessage(parsedId,
                messageService.get(getUserLocale(), "telegram.testMessage"));
        if (result.isSuccess()) {
            Notification.show(messageService.get(getUserLocale(), "notification.testSent"),
                    2000, Notification.Position.BOTTOM_CENTER);
        } else {
            Notification notification = Notification.show(
                    messageService.get(getUserLocale(), "notification.testFailed", result.description()),
                    4000, Notification.Position.BOTTOM_CENTER);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void submitFeedback() {
        String feedback = feedbackField.getValue();
        if (feedback == null || feedback.isBlank()) {
            Notification.show(messageService.get(getUserLocale(), "notification.feedbackEmpty"),
                    2500, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        feedbackService.saveFeedback(feedback.trim(), currentSettings, getUserLocale());
        feedbackField.clear();
        Notification.show(messageService.get(getUserLocale(), "notification.feedbackSent"),
                2000, Notification.Position.BOTTOM_CENTER);
    }

    @ClientCallable
    public void onTelegramInitData(String initData) {
        if (initData == null || initData.isBlank()) {
            log.debug("Telegram init data is empty");
            return;
        }
        telegramInitDataService.validateAndExtractChatId(initData)
                .ifPresentOrElse(chatId -> {
                    log.info("Telegram init data validated, updating chatId to {}", chatId);
                    chatIdField.setValue(String.valueOf(chatId));
                    userSettingsService.updateChatId(String.valueOf(chatId));
                    if (currentSettings != null) {
                        currentSettings.setChatId(String.valueOf(chatId));
                    }
                    Notification.show(messageService.get(getUserLocale(), "notification.chatIdReceived"),
                            2000, Notification.Position.BOTTOM_CENTER);
                    hideLoadingOverlay();
                }, () -> {
                    log.warn("Telegram init data failed validation");
                    Notification.show(messageService.get(getUserLocale(), "notification.telegramDataInvalid"),
                            3000, Notification.Position.BOTTOM_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    showTelegramOpenMessage();
                });
    }

    @ClientCallable
    public void onTelegramInitDataMissing() {
        showTelegramOpenMessage();
    }

    private void updateLabels() {
        Locale locale = getUserLocale();
        if (headerTitle != null) {
            headerTitle.setText(messageService.get(locale, "section.settings"));
        }
        backButton.getElement().setProperty("title", messageService.get(locale, "button.back"));
        chatIdField.setLabel(messageService.get(locale, "label.chatId"));
        chatIdField.setPlaceholder(messageService.get(locale, "placeholder.chatId"));
        timeZoneField.setLabel(messageService.get(locale, "label.timeZone"));
        maxRetryCountField.setLabel(messageService.get(locale, "label.maxRetries"));
        quietHoursStartField.setLabel(messageService.get(locale, "label.quietHoursStart"));
        quietHoursEndField.setLabel(messageService.get(locale, "label.quietHoursEnd"));
        localeField.setLabel(messageService.get(locale, "label.locale"));
        testMessageButton.setText(messageService.get(locale, "button.testMessage"));
        if (feedbackTitle != null) {
            feedbackTitle.setText(messageService.get(locale, "section.feedback"));
        }
        feedbackField.setLabel(messageService.get(locale, "label.feedback"));
        sendFeedbackButton.setText(messageService.get(locale, "button.sendFeedback"));
    }

    private void requestChatIdFromTelegram() {
        if (userSettingsService.getChatId().isPresent()) {
            hideLoadingOverlay();
            return;
        }
        getElement().executeJs(
                """
                        const component = $0;
                        const rawHash = window.location.hash || '';
                        const params = new URLSearchParams(rawHash.startsWith('#') ? rawHash.substring(1) : rawHash);
                        const initData = params.get('tgWebAppData') ?? window.Telegram?.WebApp?.initData;
                        if (!initData) {
                            component.$server.onTelegramInitDataMissing();
                            return;
                        }
                        component.$server.onTelegramInitData(initData);
                        """,
                getElement()
        );
    }

    private void updateChatIdState() {
        if (userSettingsService.getChatId().isPresent()) {
            hideLoadingOverlay();
        } else {
            showLoadingMessage(messageService.get(getUserLocale(), "loading.chatId"));
        }
    }

    private void showTelegramOpenMessage() {
        showLoadingMessage(messageService.get(getUserLocale(), "loading.telegramOpen"));
    }

    private void showLoadingMessage(String message) {
        loadingMessage.setText(message);
        loadingOverlay.setVisible(true);
    }

    private void hideLoadingOverlay() {
        loadingOverlay.setVisible(false);
    }

    private void withApplyingSettings(Runnable action) {
        applyingSettings = true;
        try {
            action.run();
        } finally {
            applyingSettings = false;
        }
    }
}
