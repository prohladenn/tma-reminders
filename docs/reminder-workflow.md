# Reminder delivery workflow

Two pivotal fields control the lifecycle of every reminder:

- `nextFireAt`: the UTC timestamp of the next attempt to deliver or re-deliver a reminder.
- `isActive`: when `false`, the reminder is skipped by the scheduler.

## Once

1. Reminders can be created for the present moment or any future time; the initial `nextFireAt` is set to that moment.
2. When `nextFireAt` is reached, the reminder is sent and `nextFireAt` is immediately moved forward by the retry interval (2 minutes).
3. If the reminder is completed, it is marked inactive to prevent further attempts.
4. If it is not completed and `nextFireAt` is reached again, a fresh message is sent and the previous one is deleted so the notification is re-triggered. `nextFireAt` is moved forward again while retries remain. After the final retry the reminder is marked inactive.

## Repeated (daily/weekly/monthly)

The flow matches the **Once** logic for retries, including message replacement. After the final retry, instead of deactivating the reminder, `nextFireAt` is moved to the next recurrence (next day, week, or month) so the cycle restarts automatically.
