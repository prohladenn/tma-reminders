package com.tma.reminders.ui;

import com.tma.reminders.reminder.Recurrence;
import com.tma.reminders.reminder.Reminder;
import com.tma.reminders.reminder.ReminderService;
import com.tma.reminders.telegram.TelegramBotService;
import com.tma.reminders.telegram.TelegramLoginService;
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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

@Route("")
@PageTitle("TMA Reminders")
@PermitAll
public class MainView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    private final ReminderService reminderService;
    private final TelegramBotService telegramBotService;
    private final UserSettingsService userSettingsService;
    private final TelegramLoginService telegramLoginService;
    private final Grid<Reminder> grid = new Grid<>(Reminder.class, false);
    private final Binder<Reminder> binder = new Binder<>(Reminder.class);
    private Reminder currentReminder;
    private final TextField chatIdField = new TextField("Telegram chat ID");

    public MainView(ReminderService reminderService, TelegramBotService telegramBotService,
                    UserSettingsService userSettingsService, TelegramLoginService telegramLoginService) {
        this.reminderService = reminderService;
        this.telegramBotService = telegramBotService;
        this.userSettingsService = userSettingsService;
        this.telegramLoginService = telegramLoginService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(buildSettings(), buildGrid(), buildForm());
        refreshGrid();
    }

    private HorizontalLayout buildSettings() {
        chatIdField.setPlaceholder("Например, 330178816");
        userSettingsService.getChatId().ifPresent(chatIdField::setValue);

        Button saveChatId = new Button("Сохранить чат", e -> saveChatId());
        saveChatId.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button testMessage = new Button("Отправить тест", e -> sendTestMessage());
        testMessage.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Button fetchFromTma = new Button("Получить из Telegram Mini App", e -> requestTmaLogin());
        fetchFromTma.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        return new HorizontalLayout(chatIdField, saveChatId, testMessage, fetchFromTma);
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
                Notification.show("Сначала сохраните Chat ID", 3000, Notification.Position.BOTTOM_CENTER)
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

    private void saveChatId() {
        String chatId = chatIdField.getValue();
        if (chatId == null || chatId.isBlank()) {
            Notification.show("Введите Chat ID из Telegram", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        userSettingsService.updateChatId(chatId.trim());
        Notification.show("Chat ID сохранен", 2000, Notification.Position.BOTTOM_CENTER);
    }

    private void sendTestMessage() {
        String chatId = userSettingsService.getChatId().orElse(null);
        if (chatId == null || chatId.isBlank()) {
            Notification.show("Сначала сохраните Chat ID", 3000, Notification.Position.BOTTOM_CENTER)
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

    private void requestTmaLogin() {
        getElement().executeJs(
                """
                        const component = $0;
                        const initData = window.Telegram?.WebApp?.initData;
                        if (!initData) {
                            component.$server.onTelegramLoginError('Откройте приложение через Telegram Mini App');
                            return;
                        }
                        fetch('/telegram/login/tma', {
                            method: 'POST',
                            headers: {
                                'Authorization': `tma ${initData}`
                            }
                        }).then(resp => {
                            if (resp.ok) {
                                return resp.json();
                            }
                            throw new Error('Вход через TMA не прошел проверку');
                        }).then(data => {
                            if (data && data.chatId) {
                                component.$server.onTelegramAuth(data.chatId);
                            } else {
                                component.$server.onTelegramLoginError('Не удалось получить chatId из ответа');
                            }
                        }).catch(err => component.$server.onTelegramLoginError(err.message));
                        """,
                getElement()
        );
    }

    @ClientCallable
    public void onTelegramAuth(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            log.debug("Telegram login returned empty chatId");
            Notification.show("Не удалось получить Chat ID из Telegram", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        log.info("Telegram login successful, updating chatId to {}", chatId);
        chatIdField.setValue(chatId);
        userSettingsService.updateChatId(chatId);
        Notification.show("Chat ID получен из Telegram", 2000, Notification.Position.BOTTOM_CENTER);
    }

    @ClientCallable
    public void onTelegramLoginError(String message) {
        log.warn("Telegram login failed on client: {}", message);
        Notification.show(message != null ? message : "Ошибка входа через Telegram",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
