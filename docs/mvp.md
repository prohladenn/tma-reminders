# MVP functionality

## Reminder types (4)
The MVP supports four recurrence types: **once**, **daily**, **weekly**, and **monthly**. Each reminder carries its recurrence type so the scheduler can determine the next send time.

## CRUD for reminders
Users manage reminders from two entry points:

- **Web UI (Vaadin)**
  - **Create** a reminder with title, optional description, start time, recurrence, and active toggle.
  - **Read/List** reminders as cards with status, next attempt, and retries.
  - **Update** reminders by selecting a card and editing fields.
  - **Delete** reminders via the delete action in the form.
- **Telegram bot**
  - **Create**: `Title; yyyy-MM-dd HH:mm; DAILY|WEEKLY|MONTHLY|ONCE; Description` (description optional)
  - **List**: `/list`
  - **Delete**: `/delete <id>`

## Delivery retries
When a reminder is due, the scheduler sends a Telegram message and immediately schedules the next attempt. If delivery fails, it retries after a short interval until the max attempt count is reached. On each retry, the previous message is replaced so the notification is re-triggered. After the final retry:

- **Once** reminders are deactivated.
- **Recurring** reminders move to the next recurrence and restart the attempt counter.

## Locales (2)
The UI is localized for **en-US** and **ru-RU**, with a locale selector in Settings. All user-facing labels, buttons, and notifications follow the chosen locale.

## Feedback form
The MVP includes a **feedback form** reachable from the UI (for example, a simple “Feedback” button). The form should:

- Accept short, free-form text from the user.
- Submit the feedback to a backend endpoint for storage or routing (e.g., email, database, or logging).
- Show a confirmation message on successful submission.
