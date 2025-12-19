package com.tma.reminders.ui;

import com.tma.reminders.reminder.Recurrence;
import com.tma.reminders.reminder.Reminder;
import com.tma.reminders.reminder.ReminderService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.converter.StringToLongConverter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.LocalDateTime;
import java.util.Arrays;

@Route("")
@PageTitle("TMA Reminders")
@PermitAll
public class MainView extends VerticalLayout {

    private final ReminderService reminderService;
    private final Grid<Reminder> grid = new Grid<>(Reminder.class, false);
    private final Binder<Reminder> binder = new Binder<>(Reminder.class);
    private Reminder currentReminder;

    public MainView(ReminderService reminderService) {
        this.reminderService = reminderService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(buildGrid(), buildForm());
        refreshGrid();
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
        TextField chatId = new TextField("Chat ID");
        TextField title = new TextField("Title");
        TextArea description = new TextArea("Description");
        DateTimePicker startTime = new DateTimePicker("Start time");
        ComboBox<Recurrence> recurrence = new ComboBox<>("Recurrence");
        recurrence.setItems(Arrays.asList(Recurrence.values()));
        recurrence.setItemLabelGenerator(Enum::name);

        binder.forField(chatId)
                .asRequired("Chat ID is required")
                .withNullRepresentation("")
                .withConverter(new StringToLongConverter("Chat ID must be a number"))
                .withValidator(id -> id > 0, "Chat ID must be positive")
                .bind(r -> {
                            String value = r.getChatId();
                            return value == null ? null : Long.valueOf(value);
                        },
                        (r, value) -> r.setChatId(value == null ? null : String.valueOf(value)));
        binder.forField(title).asRequired("Title is required").bind(Reminder::getTitle, Reminder::setTitle);
        binder.forField(description).bind(Reminder::getDescription, Reminder::setDescription);
        binder.forField(startTime)
                .asRequired("Start time is required")
                .withValidator(time -> time.isAfter(LocalDateTime.now()) || time.isEqual(LocalDateTime.now()),
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

        FormLayout formLayout = new FormLayout(chatId, title, startTime, recurrence, description, actions);
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
}
