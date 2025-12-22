package com.tma.reminders.ui;

import com.tma.reminders.reminder.Recurrence;
import com.tma.reminders.reminder.Reminder;
import com.tma.reminders.reminder.ReminderService;
import com.tma.reminders.telegram.TelegramBotService;
import com.tma.reminders.telegram.TelegramInitDataService;
import com.tma.reminders.user.UserSettingsService;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.FlexLayout.FlexWrap;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.time.ZoneOffset;

@Route("")
@PageTitle("TMA Reminders")
@PermitAll
public class MainView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    private final ReminderService reminderService;
    private final TelegramBotService telegramBotService;
    private final TelegramInitDataService telegramInitDataService;
    private final UserSettingsService userSettingsService;
    private final VerticalLayout remindersList = new VerticalLayout();
    private final Binder<Reminder> binder = new Binder<>(Reminder.class);
    private Reminder currentReminder;
    private Div selectedCard;
    private final TextField chatIdField = new TextField("Telegram chat ID (получен из Telegram)");
    private final TextField title = new TextField("Title");
    private final TextArea description = new TextArea("Description");
    private final DateTimePicker startTime = new DateTimePicker("Start time");
    private final ComboBox<Recurrence> recurrence = new ComboBox<>("Recurrence");
    private final Checkbox activeToggle = new Checkbox("Active");
    private final Button save = new Button("Save", e -> saveReminder());
    private final Button delete = new Button("Delete", e -> deleteReminder());
    private final Button reset = new Button("Reset", e -> setCurrentReminder(new Reminder()));

    public MainView(ReminderService reminderService, TelegramBotService telegramBotService,
                    TelegramInitDataService telegramInitDataService, UserSettingsService userSettingsService) {
        this.reminderService = reminderService;
        this.telegramBotService = telegramBotService;
        this.telegramInitDataService = telegramInitDataService;
        this.userSettingsService = userSettingsService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.STRETCH);

        add(buildSettings(), buildRemindersSection(), buildForm());
        refreshReminders();
        requestChatIdFromTelegram();
    }

    private FlexLayout buildSettings() {
        chatIdField.setPlaceholder("Получаем из Telegram Mini App");
        chatIdField.setReadOnly(true);
        chatIdField.setWidthFull();
        userSettingsService.getChatId().ifPresent(chatIdField::setValue);

        Button testMessage = new Button("Отправить тест", e -> sendTestMessage());
        testMessage.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        testMessage.setMinWidth("150px");
        testMessage.setWidthFull();

        FlexLayout settingsLayout = new FlexLayout(chatIdField, testMessage);
        settingsLayout.setFlexWrap(FlexWrap.WRAP);
        settingsLayout.setWidthFull();
        settingsLayout.setAlignItems(Alignment.END);
        settingsLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        settingsLayout.setFlexGrow(1, chatIdField);
        settingsLayout.getStyle().set("gap", "var(--lumo-space-s)");
        return settingsLayout;
    }

    private VerticalLayout buildRemindersSection() {
        remindersList.setPadding(false);
        remindersList.setSpacing(true);
        remindersList.setWidthFull();
        remindersList.getStyle().set("gap", "var(--lumo-space-s)");
        remindersList.getStyle().set("margin", "var(--lumo-space-s) 0");

        VerticalLayout container = new VerticalLayout(remindersList);
        container.setPadding(false);
        container.setSpacing(false);
        container.setWidthFull();
        container.getStyle().set("gap", "var(--lumo-space-xs)");
        return container;
    }

    private FormLayout buildForm() {
        title.setWidthFull();
        description.setWidthFull();
        startTime.setWidthFull();
        startTime.setStep(Duration.ofMinutes(5));
        startTime.setHelperText("Выберите время в UTC");
        startTime.setLocale(Locale.forLanguageTag("ru-RU"));
        recurrence.setWidthFull();
        recurrence.setItems(Arrays.asList(Recurrence.values()));
        recurrence.setItemLabelGenerator(Enum::name);

        binder.forField(title).asRequired("Title is required").bind(Reminder::getTitle, Reminder::setTitle);
        binder.forField(description).bind(Reminder::getDescription, Reminder::setDescription);
        binder.forField(startTime)
                .asRequired("Start time is required")
                .bind(Reminder::getStartTime, Reminder::setStartTime);
        binder.forField(recurrence)
                .asRequired("Recurrence is required")
                .bind(Reminder::getRecurrence, Reminder::setRecurrence);
        binder.forField(activeToggle)
                .bind(Reminder::isActive, Reminder::setActive);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        FlexLayout activeWrapper = new FlexLayout(activeToggle);
        activeWrapper.setAlignItems(Alignment.CENTER);

        FlexLayout actions = new FlexLayout(activeWrapper, save, delete, reset);
        actions.setFlexWrap(FlexWrap.WRAP);
        actions.setWidthFull();
        actions.setJustifyContentMode(JustifyContentMode.START);
        actions.setAlignItems(Alignment.STRETCH);
        actions.setFlexGrow(1, save, delete, reset, activeWrapper);
        save.setMinWidth("120px");
        delete.setMinWidth("120px");
        reset.setMinWidth("120px");
        activeWrapper.setMinWidth("160px");
        actions.getStyle().set("gap", "var(--lumo-space-s)");

        FormLayout formLayout = new FormLayout(title, startTime, recurrence, description, actions);
        formLayout.setColspan(description, 2);
        formLayout.setColspan(actions, 2);
        formLayout.setWidthFull();
        formLayout.setResponsiveSteps(
                new ResponsiveStep("0", 1),
                new ResponsiveStep("640px", 2)
        );
        return formLayout;
    }

    private void editReminder(Reminder reminder) {
        setCurrentReminder(reminder);
    }

    private void setCurrentReminder(Reminder reminder) {
        this.currentReminder = ensureDefaults(reminder);
        binder.readBean(this.currentReminder);
        updateEditingState(this.currentReminder);
    }

    private void refreshReminders() {
        remindersList.removeAll();
        selectedCard = null;
        List<Reminder> reminders = reminderService.findAll();

        if (reminders.isEmpty()) {
            Paragraph emptyState = new Paragraph("Напоминаний пока нет");
            emptyState.getStyle().set("color", "var(--lumo-contrast-50pct)");
            remindersList.add(emptyState);
        } else {
            reminders.stream()
                    .sorted(Comparator.comparing(Reminder::getStartTime))
                    .forEach(reminder -> remindersList.add(createReminderCard(reminder)));
        }

        setCurrentReminder(new Reminder());
    }

    private Div createReminderCard(Reminder reminder) {
        Div card = new Div();
        card.addClassName("reminder-card");
        card.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        card.getStyle().set("border-radius", "12px");
        card.getStyle().set("padding", "var(--lumo-space-m)");
        card.getStyle().set("background", "var(--lumo-base-color)");
        card.getStyle().set("box-shadow", "0 2px 4px 0 var(--lumo-shade-5pct)");
        card.getStyle().set("cursor", "pointer");
        card.getStyle().set("transition", "box-shadow 120ms ease, border-color 120ms ease");

        Span title = new Span(reminder.getTitle());
        title.getStyle().set("font-weight", "600");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)");

        Span nextRun = new Span("⏰ " + formatDateTime(reminder.getStartTime()));
        nextRun.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span recurrence = new Span("🔁 " + reminder.getRecurrence().name());
        recurrence.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span active = new Span(reminder.isActive() ? "Активно" : "Выключено");
        active.getStyle().set("color", reminder.isActive() ? "var(--lumo-success-text-color)" : "var(--lumo-error-text-color)");
        active.getStyle().set("font-weight", "500");

        Paragraph description = new Paragraph(reminder.getDescription() == null || reminder.getDescription().isBlank()
                ? "Без описания"
                : reminder.getDescription());
        description.getStyle().set("margin", "var(--lumo-space-xs) 0");
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");

        FlexLayout meta = new FlexLayout(nextRun, recurrence, active);
        meta.setFlexWrap(FlexWrap.WRAP);
        meta.setAlignItems(Alignment.CENTER);
        meta.setJustifyContentMode(JustifyContentMode.START);
        meta.setWidthFull();
        meta.getStyle().set("gap", "var(--lumo-space-m)");
        meta.getStyle().set("margin-top", "var(--lumo-space-xs)");

        card.add(title, description, meta);
        card.addClickListener(event -> selectReminder(reminder, card));
        return card;
    }

    private void selectReminder(Reminder reminder, Div card) {
        editReminder(reminder);

        if (selectedCard != null) {
            selectedCard.getStyle().remove("border-color");
            selectedCard.getStyle().remove("box-shadow");
        }

        selectedCard = card;
        selectedCard.getStyle().set("border-color", "var(--lumo-primary-color-50pct)");
        selectedCard.getStyle().set("box-shadow", "0 4px 10px 0 var(--lumo-shade-10pct)");
    }

    private void saveReminder() {
        if (currentReminder == null) {
            currentReminder = new Reminder();
        }
        if (binder.writeBeanIfValid(currentReminder)) {
            var chatId = userSettingsService.getChatId().orElse(null);
            if (chatId == null || chatId.isBlank()) {
                Notification.show("Chat ID пока не получен из Telegram", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            currentReminder.setChatId(chatId);
            reminderService.save(currentReminder);
            Notification.show("Reminder saved", 2000, Notification.Position.BOTTOM_CENTER);
            refreshReminders();
        } else {
            Notification.show("Please fix validation errors", 2000, Notification.Position.BOTTOM_CENTER);
        }
    }

    private void deleteReminder() {
        if (currentReminder != null && currentReminder.getId() != null) {
            reminderService.delete(currentReminder.getId());
            Notification.show("Reminder deleted", 2000, Notification.Position.BOTTOM_CENTER);
            refreshReminders();
        }
    }

    private void sendTestMessage() {
        String chatId = userSettingsService.getChatId().orElse(null);
        if (chatId == null || chatId.isBlank()) {
            Notification.show("Chat ID пока не получен из Telegram", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        Long parsedId;
        try {
            parsedId = Long.valueOf(chatId);
        } catch (NumberFormatException ex) {
            Notification.show("Chat ID должен быть числом", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        var result = telegramBotService.sendMessage(parsedId, "Тестовое сообщение от TMA Reminders");
        if (result.isSuccess()) {
            Notification.show("Тестовое сообщение отправлено", 2000, Notification.Position.BOTTOM_CENTER);
        } else {
            Notification notification = Notification.show("Не удалось отправить: " + result.description(),
                    4000, Notification.Position.BOTTOM_CENTER);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
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
                    Notification.show("Chat ID получен из Telegram", 2000, Notification.Position.BOTTOM_CENTER);
                }, () -> {
                    log.warn("Telegram init data failed validation");
                    Notification.show("Не удалось подтвердить Telegram данные", 3000, Notification.Position.BOTTOM_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                });
    }

    private void requestChatIdFromTelegram() {
        getElement().executeJs(
                """
                        const component = $0;
                        const rawHash = window.location.hash || '';
                        const params = new URLSearchParams(rawHash.startsWith('#') ? rawHash.substring(1) : rawHash);
                        const initData = params.get('tgWebAppData') ?? window.Telegram?.WebApp?.initData;
                        if (!initData) {
                            return;
                        }
                        component.$server.onTelegramInitData(initData);
                        """,
                getElement()
        );
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'"));
    }

    private Reminder ensureDefaults(Reminder reminder) {
        if (reminder.getStartTime() == null) {
            reminder.setStartTime(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5).withSecond(0).withNano(0));
        }
        if (reminder.getRecurrence() == null) {
            reminder.setRecurrence(Recurrence.ONCE);
        }
        return reminder;
    }

    private void updateEditingState(Reminder reminder) {
        title.setReadOnly(false);
        description.setReadOnly(false);
        startTime.setReadOnly(false);
        recurrence.setReadOnly(false);
        activeToggle.setReadOnly(false);
        save.setEnabled(true);
        activeToggle.setEnabled(true);
        startTime.setMin(null);
    }
}
