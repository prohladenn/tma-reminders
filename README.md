# TMA Reminders

Мини-приложение Telegram на Spring Boot + Vaadin для создания напоминаний.

## Возможности
- Создание напоминаний с повторением (once/daily/weekly/monthly) через Telegram-бота или веб-интерфейс.
- Просмотр и удаление напоминаний.
- Отправка уведомлений пользователю в запланированное время.

## Health-check
- HTTP: `GET /health` → `{"status":"UP"}`

## Запуск
1. Установите переменные окружения `TELEGRAM_BOT_USERNAME` и `TELEGRAM_BOT_TOKEN`.
2. Запустите приложение:
   ```bash
   mvn spring-boot:run
   ```
3. В браузере откройте `http://localhost:8080` для UI (Vaadin).

## Деплой на Railway (Docker)

В репозитории есть `Dockerfile` c production-сборкой Vaadin. Минимальные шаги для Railway:

1. Создайте проект Railway с PostgreSQL и бекендом из этого репозитория.
2. Укажите переменные окружения:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `DATABASE_URL=jdbc:postgresql://<host>:<port>/<db>` (Railway выдаёт строку подключения; убедитесь, что она в JDBC-формате)
   - `DB_USER` / `DB_PASSWORD`
   - `TELEGRAM_BOT_TOKEN_TEST`, `TELEGRAM_BOT_TOKEN_PROD`
   - `TELEGRAM_BOT_TOKEN_ENV=prod` (использовать prod-бота)
3. Railway автоматически подставляет `PORT`; приложение слушает его (фоллбек 8080).

Локальная проверка образа:
```bash
docker build -t tma-reminders .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=jdbc:postgresql://localhost:5432/postgres \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  -e TELEGRAM_BOT_TOKEN_PROD=... \
  -e TELEGRAM_BOT_TOKEN_TEST=... \
  -e TELEGRAM_BOT_TOKEN_ENV=prod \
  tma-reminders
```

### Формат команд в Telegram
- Создать:  
  `Заголовок; yyyy-MM-dd HH:mm; DAILY|WEEKLY|MONTHLY|ONCE; Описание` (описание опционально)
- Список: `/list`
- Удаление: `/delete <id>`
