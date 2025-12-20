# TMA Reminders

Мини-приложение Telegram на Spring Boot + Vaadin для создания напоминаний.

## Возможности
- Создание напоминаний с повторением (once/daily/weekly/monthly) через Telegram-бота или веб-интерфейс.
- Просмотр и удаление напоминаний.
- Отправка уведомлений пользователю в запланированное время.

## Запуск
1. Установите переменные окружения `TELEGRAM_BOT_USERNAME` и `TELEGRAM_BOT_TOKEN`.
2. Запустите приложение:
   ```bash
   mvn spring-boot:run
   ```
3. В браузере откройте `http://localhost:8080` для UI (Vaadin).

### Формат команд в Telegram
- Создать:  
  `Заголовок; yyyy-MM-dd HH:mm; DAILY|WEEKLY|MONTHLY|ONCE; Описание` (описание опционально)
- Список: `/list`
- Удаление: `/delete <id>`
