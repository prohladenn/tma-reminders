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
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;

@Route("")
@PageTitle("TMA Reminders")
@PermitAll
public class MainView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    private final ReminderService reminderService;
    private final TelegramBotService telegramBotService;
    private final TelegramInitDataService telegramInitDataService;
    private final UserSettingsService userSettingsService;
    private final Grid<Reminder> grid = new Grid<>(Reminder.class, false);
    private final Binder<Reminder> binder = new Binder<>(Reminder.class);
    private Reminder currentReminder;
    private final TextField chatIdField = new TextField("Telegram chat ID (получен из Telegram)");

    public MainView(ReminderService reminderService, TelegramBotService telegramBotService,
                    TelegramInitDataService telegramInitDataService, UserSettingsService userSettingsService) {
        this.reminderService = reminderService;
        this.telegramBotService = telegramBotService;
        this.telegramInitDataService = telegramInitDataService;
        this.userSettingsService = userSettingsService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(buildSettings(), buildGrid(), buildForm());
        refreshGrid();
        requestChatIdFromTelegram();
    }

    private HorizontalLayout buildSettings() {
        chatIdField.setPlaceholder("Получаем из Telegram Mini App");
        chatIdField.setReadOnly(true);
        userSettingsService.getChatId().ifPresent(chatIdField::setValue);

        Button testMessage = new Button("Отправить тест", e -> sendTestMessage());
        testMessage.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        return new HorizontalLayout(chatIdField, testMessage);
    }

    private Grid<Reminder> buildGrid() {
        grid.addColumn(Reminder::getId).setHeader("ID").setAutoWidth(true);
        grid.addColumn(Reminder::getChatId).setHeader("Chat ID").setAutoWidth(true);
        grid.addColumn(Reminder::getTitle).setHeader("Title").setAutoWidth(true);
        grid.addColumn(Reminder::getStartTime).setHeader("Next run").setAutoWidth(true);
        grid.addColumn(Reminder::getRecurrence).setHeader("Recurrence").setAutoWidth(true);
        grid.addColumn(Reminder::isActive).setHeader("Active").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setHeight("350px");
        grid.addSelectionListener(event -> event.getFirstSelectedItem().ifPresent(this::editReminder));
        return grid;
    }

    private FormLayout buildForm() {
        TextField title = new TextField("Title");
        TextArea description = new TextArea("Description");
        DateTimePicker startTime = new DateTimePicker("Start time");
        ComboBox<Recurrence> recurrence = new ComboBox<>("Recurrence");
        recurrence.setItems(Arrays.asList(Recurrence.values()));
        recurrence.setItemLabelGenerator(Enum::name);

        binder.forField(title).asRequired("Title is required").bind(Reminder::getTitle, Reminder::setTitle);
        binder.forField(description).bind(Reminder::getDescription, Reminder::setDescription);
        binder.forField(startTime)
                .asRequired("Start time is required")
                .withValidator(time -> time != null && (time.isAfter(LocalDateTime.now()) || time.isEqual(LocalDateTime.now())),
                        "Time must be in the future")
                .bind(Reminder::getStartTime, Reminder::setStartTime);
        binder.forField(recurrence)
                .asRequired("Recurrence is required")
                .bind(Reminder::getRecurrence, Reminder::setRecurrence);

        Button save = new Button("Save", e -> saveReminder());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button delete = new Button("Delete", e -> deleteReminder());
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        Button reset = new Button("Reset", e -> setCurrentReminder(new Reminder()));

        HorizontalLayout actions = new HorizontalLayout(save, delete, reset);

        FormLayout formLayout = new FormLayout(title, startTime, recurrence, description, actions);
        formLayout.setColspan(description, 2);
        return formLayout;
    }

    private void editReminder(Reminder reminder) {
        setCurrentReminder(reminder);
    }

    private void setCurrentReminder(Reminder reminder) {
        this.currentReminder = reminder;
        binder.readBean(reminder);
    }

    private void refreshGrid() {
        grid.setItems(reminderService.findAll());
        grid.deselectAll();
        setCurrentReminder(new Reminder());
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
            refreshGrid();
        } else {
            Notification.show("Please fix validation errors", 2000, Notification.Position.BOTTOM_CENTER);
        }
    }

    private void deleteReminder() {
        if (currentReminder != null && currentReminder.getId() != null) {
            reminderService.delete(currentReminder.getId());
            Notification.show("Reminder deleted", 2000, Notification.Position.BOTTOM_CENTER);
            refreshGrid();
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
}
