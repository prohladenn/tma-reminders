package com.tma.reminders.ui;

import com.tma.reminders.i18n.MessageService;
import com.tma.reminders.reminder.Recurrence;
import com.tma.reminders.reminder.Reminder;
import com.tma.reminders.reminder.ReminderService;
import com.tma.reminders.telegram.TelegramInitDataService;
import com.tma.reminders.user.UserSettings;
import com.tma.reminders.user.UserSettingsService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.FlexLayout.FlexWrap;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Route("")
@PageTitle("TMA Reminders")
@PermitAll
public class MainView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    private final ReminderService reminderService;
    private final TelegramInitDataService telegramInitDataService;
    private final UserSettingsService userSettingsService;
    private final MessageService messageService;
    private final VerticalLayout remindersList = new VerticalLayout();
    private final Binder<Reminder> binder = new Binder<>(Reminder.class);
    private Reminder currentReminder;
    private Div selectedCard;
    private final TextField title = new TextField();
    private final TextArea description = new TextArea();
    private final DateTimePicker startTime = new DateTimePicker();
    private final ComboBox<Recurrence> recurrence = new ComboBox<>();
    private final Checkbox activeToggle = new Checkbox();
    private final Button save = new Button();
    private final Button delete = new Button();
    private final Button reset = new Button();
    private final Button newReminderButton = new Button();
    private final Dialog reminderDialog = new Dialog();
    private final Dialog deleteDialog = new Dialog();
    private final Paragraph deleteDialogMessage = new Paragraph();
    private final Button deleteConfirmButton = new Button();
    private final Button deleteCancelButton = new Button();
    private final Div loadingOverlay = new Div();
    private final Paragraph loadingMessage = new Paragraph();
    private final ProgressBar loadingBar = new ProgressBar();
    private UserSettings currentSettings;
    private H2 headerTitle;
    private H2 remindersTitle;

    public MainView(ReminderService reminderService, TelegramInitDataService telegramInitDataService,
                    UserSettingsService userSettingsService, MessageService messageService) {
        this.reminderService = reminderService;
        this.telegramInitDataService = telegramInitDataService;
        this.userSettingsService = userSettingsService;
        this.messageService = messageService;
        this.currentSettings = userSettingsService.getSettings();
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.STRETCH);
        applySafeAreaStyles();
        save.addClickListener(event -> saveReminder());
        delete.addClickListener(event -> openDeleteConfirm());
        reset.addClickListener(event -> setCurrentReminder(new Reminder()));
        newReminderButton.addClickListener(event -> startNewReminder());

        add(buildHeader(), buildRemindersSection(), buildReminderDialog(), buildDeleteDialog(), buildLoadingOverlay());
        refreshReminders();
        updateChatIdState();
        requestChatIdFromTelegram();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        refreshSettings();
        attachEvent.getUI().getPage().setTitle(messageService.get(getUserLocale(), "app.title"));
        configureTelegramViewport();
        configureTelegramBackButton(false);
        updateSwipeBehavior();
    }

    private HorizontalLayout buildHeader() {
        headerTitle = new H2();
        headerTitle.getStyle().set("margin", "0");

        Button settingsButton = new Button(new Icon(VaadinIcon.COG), event -> getUI()
                .ifPresent(ui -> ui.navigate(SettingsView.class)));
        settingsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        settingsButton.getElement().setProperty("title", messageService.get(getUserLocale(), "button.settings"));

        HorizontalLayout header = new HorizontalLayout(headerTitle, settingsButton);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        updateLabels();
        return header;
    }

    private VerticalLayout buildRemindersSection() {
        remindersTitle = new H2();
        remindersTitle.getStyle().set("margin", "0");

        newReminderButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout header = new HorizontalLayout(remindersTitle, newReminderButton);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        remindersList.setPadding(false);
        remindersList.setSpacing(true);
        remindersList.setWidthFull();
        remindersList.getStyle().set("gap", "var(--lumo-space-s)");
        remindersList.getStyle().set("margin", "var(--lumo-space-s) 0");

        VerticalLayout container = new VerticalLayout(header, remindersList);
        container.setPadding(false);
        container.setSpacing(false);
        container.setWidthFull();
        container.getStyle().set("gap", "var(--lumo-space-xs)");
        return container;
    }

    private Dialog buildReminderDialog() {
        reminderDialog.setHeaderTitle(messageService.get(getUserLocale(), "dialog.newReminder"));
        reminderDialog.setModal(true);
        reminderDialog.setDraggable(true);
        reminderDialog.setResizable(true);
        reminderDialog.addOpenedChangeListener(event -> updateSwipeBehavior());
        reminderDialog.add(buildForm());
        reminderDialog.setWidth("720px");
        return reminderDialog;
    }

    private FormLayout buildForm() {
        updateLabels();
        title.setWidthFull();
        description.setWidthFull();
        startTime.setWidthFull();
        startTime.setStep(Duration.ofMinutes(5));
        updateDateTimeUiSettings();
        recurrence.setWidthFull();
        recurrence.setItems(Arrays.asList(Recurrence.values()));
        recurrence.setItemLabelGenerator(value -> messageService.get(getUserLocale(),
                "recurrence." + value.name().toLowerCase(Locale.ROOT)));

        binder.forField(title)
                .asRequired(messageService.get(getUserLocale(), "validation.titleRequired"))
                .bind(Reminder::getTitle, Reminder::setTitle);
        binder.forField(description).bind(Reminder::getDescription, Reminder::setDescription);
        binder.forField(startTime)
                .asRequired(messageService.get(getUserLocale(), "validation.startTimeRequired"))
                .bind(reminder -> convertFromUtc(reminder.getStartTime()),
                        (reminder, value) -> reminder.setStartTime(convertToUtc(value)));
        binder.forField(recurrence)
                .asRequired(messageService.get(getUserLocale(), "validation.recurrenceRequired"))
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

        Hr divider = new Hr();
        divider.getStyle().set("margin", "var(--lumo-space-s) 0");

        FormLayout formLayout = new FormLayout(title, startTime, recurrence, description, divider, actions);
        formLayout.setColspan(description, 2);
        formLayout.setColspan(divider, 2);
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
        openReminderDialog(messageService.get(getUserLocale(), "dialog.editReminder"));
    }

    private void setCurrentReminder(Reminder reminder) {
        this.currentReminder = ensureDefaults(reminder);
        binder.readBean(this.currentReminder);
        updateEditingState(this.currentReminder);
    }

    private void startNewReminder() {
        clearSelectedCard();
        setCurrentReminder(new Reminder());
        openReminderDialog(messageService.get(getUserLocale(), "dialog.newReminder"));
    }

    private void refreshReminders() {
        remindersList.removeAll();
        selectedCard = null;
        List<Reminder> reminders = reminderService.findAll();

        if (reminders.isEmpty()) {
            Paragraph emptyState = new Paragraph(messageService.get(getUserLocale(), "emptyState.reminders"));
            emptyState.getStyle().set("color", "var(--lumo-contrast-50pct)");
            Button createFirst = new Button(messageService.get(getUserLocale(), "button.createReminder"),
                    event -> startNewReminder());
            createFirst.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            FlexLayout emptyLayout = new FlexLayout(emptyState, createFirst);
            emptyLayout.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
            emptyLayout.setAlignItems(Alignment.START);
            emptyLayout.getStyle().set("gap", "var(--lumo-space-xs)");
            remindersList.add(emptyLayout);
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

        Span activePill = new Span(reminder.isActive()
                ? messageService.get(getUserLocale(), "status.active")
                : messageService.get(getUserLocale(), "status.inactive"));
        activePill.getStyle().set("padding", "0 var(--lumo-space-s)");
        activePill.getStyle().set("border-radius", "999px");
        activePill.getStyle().set("font-size", "var(--lumo-font-size-s)");
        activePill.getStyle().set("font-weight", "600");
        activePill.getStyle().set("background-color",
                reminder.isActive() ? "var(--lumo-success-10pct)" : "var(--lumo-error-10pct)");
        activePill.getStyle().set("color",
                reminder.isActive() ? "var(--lumo-success-text-color)" : "var(--lumo-error-text-color)");

        Checkbox quickToggle = new Checkbox();
        quickToggle.setValue(reminder.isActive());
        quickToggle.getElement().getThemeList().addAll(List.of("toggle", "small"));
        quickToggle.getElement().setProperty("aria-label",
                messageService.get(getUserLocale(), "aria.quickToggle"));
        quickToggle.getElement().executeJs("this.addEventListener('click', e => e.stopPropagation())");
        quickToggle.addValueChangeListener(event -> {
            reminder.setActive(event.getValue());
            reminderService.save(reminder);
            refreshReminders();
        });

        HorizontalLayout statusControls = new HorizontalLayout(activePill, quickToggle);
        statusControls.setAlignItems(Alignment.CENTER);
        statusControls.setSpacing(true);
        statusControls.getStyle().set("gap", "var(--lumo-space-xs)");

        FlexLayout header = new FlexLayout(title, statusControls);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        Span nextRun = new Span(messageService.get(getUserLocale(), "label.nextRun",
                formatDateTime(reminder.getStartTime())));
        nextRun.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span recurrence = new Span(messageService.get(getUserLocale(), "label.recurrence",
                messageService.get(getUserLocale(), "recurrence." + reminder.getRecurrence().name().toLowerCase(Locale.ROOT))));
        recurrence.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span status = new Span(messageService.get(getUserLocale(), "label.status",
                reminder.isActive()
                        ? messageService.get(getUserLocale(), "status.active")
                        : messageService.get(getUserLocale(), "status.inactive")));
        status.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Paragraph description = new Paragraph(reminder.getDescription() == null || reminder.getDescription().isBlank()
                ? messageService.get(getUserLocale(), "label.noDescription")
                : reminder.getDescription());
        description.getStyle().set("margin", "var(--lumo-space-xs) 0");
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");

        FlexLayout meta = new FlexLayout(nextRun, recurrence, status);
        meta.setFlexWrap(FlexWrap.WRAP);
        meta.setAlignItems(Alignment.CENTER);
        meta.setJustifyContentMode(JustifyContentMode.START);
        meta.setWidthFull();
        meta.getStyle().set("gap", "var(--lumo-space-m)");
        meta.getStyle().set("margin-top", "var(--lumo-space-xs)");

        Span lastSent = new Span(messageService.get(getUserLocale(), "label.lastSent",
                formatDateTimeOrDash(reminder.getLastSentAt())));
        lastSent.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span nextAttempt = new Span(messageService.get(getUserLocale(), "label.nextAttempt",
                formatDateTimeOrDash(reminder.getNextAttemptAt())));
        nextAttempt.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span retries = new Span(messageService.get(getUserLocale(), "label.retries",
                reminder.getSendAttempts()));
        retries.getStyle().set("color",
                reminder.getSendAttempts() > 0 ? "var(--lumo-error-text-color)" : "var(--lumo-secondary-text-color)");
        retries.getStyle().set("font-weight", reminder.getSendAttempts() > 0 ? "600" : "400");

        FlexLayout statusRow = new FlexLayout(lastSent, nextAttempt, retries);
        statusRow.setFlexWrap(FlexWrap.WRAP);
        statusRow.setAlignItems(Alignment.CENTER);
        statusRow.setJustifyContentMode(JustifyContentMode.START);
        statusRow.setWidthFull();
        statusRow.getStyle().set("gap", "var(--lumo-space-m)");
        statusRow.getStyle().set("margin-top", "var(--lumo-space-xs)");

        card.add(header, description, meta, statusRow);
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
                Notification.show(messageService.get(getUserLocale(), "notification.chatIdMissing"),
                        3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            currentReminder.setChatId(chatId);
            reminderService.save(currentReminder);
            Notification.show(messageService.get(getUserLocale(), "notification.reminderSaved"),
                    2000, Notification.Position.BOTTOM_CENTER);
            refreshReminders();
            reminderDialog.close();
        } else {
            Notification.show(messageService.get(getUserLocale(), "notification.validationError"),
                    2000, Notification.Position.BOTTOM_CENTER);
        }
    }

    private void openDeleteConfirm() {
        if (currentReminder == null || currentReminder.getId() == null) {
            return;
        }
        deleteDialog.open();
    }

    private Dialog buildDeleteDialog() {
        deleteDialog.setHeaderTitle(messageService.get(getUserLocale(), "dialog.deleteTitle"));
        deleteDialogMessage.setText(messageService.get(getUserLocale(), "dialog.deleteMessage"));
        deleteConfirmButton.setText(messageService.get(getUserLocale(), "button.delete"));
        deleteDialog.addOpenedChangeListener(event -> updateSwipeBehavior());
        deleteConfirmButton.addClickListener(event -> {
            reminderService.delete(currentReminder.getId());
            Notification.show(messageService.get(getUserLocale(), "notification.reminderDeleted"),
                    2000, Notification.Position.BOTTOM_CENTER);
            refreshReminders();
            reminderDialog.close();
            deleteDialog.close();
        });
        deleteConfirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        deleteCancelButton.setText(messageService.get(getUserLocale(), "button.cancel"));
        deleteCancelButton.addClickListener(event -> deleteDialog.close());
        deleteDialog.add(deleteDialogMessage);
        deleteDialog.getFooter().add(deleteCancelButton, deleteConfirmButton);
        return deleteDialog;
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

    @ClientCallable
    public void onTelegramInitData(String initData) {
        if (initData == null || initData.isBlank()) {
            log.debug("Telegram init data is empty");
            return;
        }
        telegramInitDataService.validateAndExtractChatId(initData)
                .ifPresentOrElse(chatId -> {
                    log.info("Telegram init data validated, updating chatId to {}", chatId);
                    userSettingsService.updateChatId(String.valueOf(chatId));
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

    private void updateDateTimeUiSettings() {
        ZoneId zoneId = getUserZoneId();
        startTime.setHelperText(messageService.get(getUserLocale(), "helper.startTime", zoneId.getId()));
        startTime.setLocale(getUserLocale());
    }

    private void updateLabels() {
        Locale locale = getUserLocale();
        if (headerTitle != null) {
            headerTitle.setText(messageService.get(locale, "app.title"));
        }
        if (remindersTitle != null) {
            remindersTitle.setText(messageService.get(locale, "section.reminders"));
        }
        title.setLabel(messageService.get(locale, "label.title"));
        description.setLabel(messageService.get(locale, "label.description"));
        startTime.setLabel(messageService.get(locale, "label.startTime"));
        recurrence.setLabel(messageService.get(locale, "label.recurrenceLabel"));
        activeToggle.setLabel(messageService.get(locale, "label.active"));
        save.setText(messageService.get(locale, "button.save"));
        delete.setText(messageService.get(locale, "button.delete"));
        reset.setText(messageService.get(locale, "button.reset"));
        newReminderButton.setText(messageService.get(locale, "button.newReminder"));
    }

    private void refreshSettings() {
        currentSettings = userSettingsService.getSettings();
        updateLabels();
        updateDateTimeUiSettings();
        refreshReminders();
        updateDialogLabels();
        updateChatIdState();
    }

    private void updateDialogLabels() {
        reminderDialog.setHeaderTitle(messageService.get(getUserLocale(), "dialog.newReminder"));
        deleteDialog.setHeaderTitle(messageService.get(getUserLocale(), "dialog.deleteTitle"));
        deleteDialogMessage.setText(messageService.get(getUserLocale(), "dialog.deleteMessage"));
        deleteConfirmButton.setText(messageService.get(getUserLocale(), "button.delete"));
        deleteCancelButton.setText(messageService.get(getUserLocale(), "button.cancel"));
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z").withLocale(getUserLocale());
        return dateTime.atZone(ZoneOffset.UTC).withZoneSameInstant(getUserZoneId()).format(formatter);
    }

    private String formatDateTimeOrDash(LocalDateTime dateTime) {
        String formatted = formatDateTime(dateTime);
        return formatted == null || formatted.isBlank() ? "—" : formatted;
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
        delete.setEnabled(reminder != null && reminder.getId() != null);
        startTime.setMin(null);
    }

    private void openReminderDialog(String title) {
        reminderDialog.setHeaderTitle(title);
        reminderDialog.open();
    }

    private void clearSelectedCard() {
        if (selectedCard != null) {
            selectedCard.getStyle().remove("border-color");
            selectedCard.getStyle().remove("box-shadow");
            selectedCard = null;
        }
    }

    private ZoneId getUserZoneId() {
        if (currentSettings == null || currentSettings.getTimeZoneId() == null || currentSettings.getTimeZoneId().isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(currentSettings.getTimeZoneId());
        } catch (DateTimeException ex) {
            return ZoneOffset.UTC;
        }
    }

    private Locale getUserLocale() {
        if (currentSettings == null || currentSettings.getLocale() == null || currentSettings.getLocale().isBlank()) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(currentSettings.getLocale());
    }

    private LocalDateTime convertFromUtc(LocalDateTime utcDateTime) {
        if (utcDateTime == null) {
            return null;
        }
        return utcDateTime.atZone(ZoneOffset.UTC).withZoneSameInstant(getUserZoneId()).toLocalDateTime();
    }

    private LocalDateTime convertToUtc(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(getUserZoneId()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private void configureTelegramBackButton(boolean visible) {
        postTelegramEvent("web_app_setup_back_button",
                TelegramWebAppSupport.backButtonEventData(visible));
    }

    private void updateSwipeBehavior() {
        boolean allowSwipe = !(reminderDialog.isOpened() || deleteDialog.isOpened());
        postTelegramEvent("web_app_setup_swipe_behavior",
                TelegramWebAppSupport.swipeBehaviorEventData(allowSwipe));
    }

    private void configureTelegramViewport() {
        postTelegramEvent("web_app_expand", null);
        postTelegramEvent("web_app_request_viewport", null);
        postTelegramEvent("web_app_request_safe_area", null);
        postTelegramEvent("web_app_request_content_safe_area", null);
        registerViewportEventHandlers();
    }

    private void applySafeAreaStyles() {
        TelegramWebAppSupport.safeAreaStyles()
                .forEach((key, value) -> getElement().getStyle().set(key, value));
    }

    private void registerViewportEventHandlers() {
        getElement().executeJs(
                """
                        const component = $0;
                        const applyInsets = (insets) => {
                          const top = insets?.top ?? 0;
                          const bottom = insets?.bottom ?? 0;
                          const left = insets?.left ?? 0;
                          const right = insets?.right ?? 0;
                          component.style.setProperty('--tg-safe-area-top', `${top}px`);
                          component.style.setProperty('--tg-safe-area-bottom', `${bottom}px`);
                          component.style.setProperty('--tg-safe-area-left', `${left}px`);
                          component.style.setProperty('--tg-safe-area-right', `${right}px`);
                        };
                        const applyViewport = (webApp) => {
                          if (!webApp) {
                            return;
                          }
                          if (webApp.viewportHeight) {
                            component.style.setProperty('--tg-viewport-height', `${webApp.viewportHeight}px`);
                          }
                          if (webApp.viewportStableHeight) {
                            component.style.setProperty('--tg-viewport-stable-height', `${webApp.viewportStableHeight}px`);
                          }
                        };
                        const webApp = window.Telegram?.WebApp;
                        if (!webApp?.onEvent) {
                          return;
                        }
                        const safeArea = webApp.safeAreaInset || webApp.safeAreaInsets || webApp.safeArea || {};
                        const contentSafeArea = webApp.contentSafeAreaInset || webApp.contentSafeArea || {};
                        applyInsets(Object.keys(contentSafeArea).length ? contentSafeArea : safeArea);
                        applyViewport(webApp);
                        webApp.onEvent('safeAreaChanged', () => {
                          const nextSafeArea = webApp.safeAreaInset || webApp.safeAreaInsets || webApp.safeArea || {};
                          applyInsets(nextSafeArea);
                        });
                        webApp.onEvent('contentSafeAreaChanged', () => {
                          const nextContentSafeArea = webApp.contentSafeAreaInset || webApp.contentSafeArea || {};
                          const fallbackSafeArea = webApp.safeAreaInset || webApp.safeAreaInsets || webApp.safeArea || {};
                          applyInsets(Object.keys(nextContentSafeArea).length ? nextContentSafeArea : fallbackSafeArea);
                        });
                        webApp.onEvent('viewportChanged', () => applyViewport(webApp));
                        """,
                getElement()
        );
    }

    private void postTelegramEvent(String eventType, String eventDataJson) {
        getElement().executeJs(
                """
                        const eventType = $0;
                        const eventDataJson = $1;
                        const eventData = eventDataJson ? JSON.parse(eventDataJson) : {};
                        const payload = JSON.stringify({ eventType, eventData });
                        if (window.TelegramWebviewProxy?.postEvent) {
                          window.TelegramWebviewProxy.postEvent(eventType, JSON.stringify(eventData));
                          return;
                        }
                        if (window.external?.notify) {
                          window.external.notify(payload);
                          return;
                        }
                        if (window.parent?.postMessage) {
                          window.parent.postMessage(payload, 'https://web.telegram.org');
                        }
                        """,
                eventType,
                eventDataJson
        );
    }
}
