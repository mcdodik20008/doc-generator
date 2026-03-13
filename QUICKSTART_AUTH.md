# Быстрый старт: Аутентификация

## Запуск приложения с аутентификацией

### 1. Поднимите БД и сервисы

```bash
docker compose up -d
```

Это запустит:
- PostgreSQL 16 на порту **5434**
- Redis на порту 6379
- Ollama на порту 11434

### 2. Запустите приложение

```bash
gradlew.bat bootRun
```

### 3. Откройте браузер

Перейдите на http://localhost:8080

Вы будете автоматически перенаправлены на страницу логина.

### 4. Войдите в систему

Используйте учетные данные по умолчанию:
- **Username**: `admin`
- **Password**: `admin123!@#`

## Отключение аутентификации (для разработки)

Если вы хотите отключить аутентификацию во время разработки:

### Вариант 1: Через application.yml

Отредактируйте `src/main/resources/application.yml`:

```yaml
docgen:
  security:
    enabled: false
```

### Вариант 2: Через переменную окружения

```bash
set DOCGEN_SECURITY_ENABLED=false
gradlew.bat bootRun
```

### Вариант 3: Через аргументы запуска

```bash
gradlew.bat bootRun --args="--docgen.security.enabled=false"
```

## Управление пользователями

### Добавить нового пользователя

1. Сгенерируйте BCrypt хэш пароля:

```kotlin
// Запустите тест PasswordHashGenerator
gradlew.bat test --tests "com.bftcom.docgenerator.util.PasswordHashGenerator"
```

2. Добавьте пользователя в БД:

```sql
INSERT INTO doc_generator.users (username, password_hash, email, enabled, roles)
VALUES (
    'newuser',
    '$2a$10$YourGeneratedHashHere',
    'newuser@example.com',
    true,
    '{USER}'  -- Или '{ADMIN,USER}' для админа
);
```

### Изменить пароль существующего пользователя

```sql
-- 1. Сгенерируйте новый BCrypt хэш (см. выше)
-- 2. Обновите в БД:
UPDATE doc_generator.users
SET password_hash = '$2a$10$YourNewHashHere'
WHERE username = 'admin';
```

## Работа с API

Для работы с REST API (`/api/**`) используйте API ключи:

### Создать API ключ

```sql
-- Сгенерируйте SHA-256 хэш вашего ключа
-- Например: echo -n "sk_live_mykey123" | sha256sum
-- Результат: a8f5f167f44f4964e6c998dee827110c

INSERT INTO doc_generator.api_key (name, key_hash, scopes, active)
VALUES (
    'my-api-key',
    'a8f5f167f44f4964e6c998dee827110c',  -- SHA-256 хэш
    '{read,write}',
    true
);
```

### Использовать API ключ

```bash
curl -H "X-API-Key: sk_live_mykey123" \
     http://localhost:8080/api/applications
```

## Troubleshooting

### Проблема: "Invalid username or password"

1. Проверьте, что вы используете правильные учетные данные
2. Проверьте, что пользователь активен (`enabled = true`)
3. Проверьте логи: `logs/doc-generator.log`

### Проблема: Не могу войти после изменения пароля

1. Убедитесь, что вы сгенерировали правильный BCrypt хэш
2. Проверьте, что хэш сохранен полностью (60 символов)
3. Перезапустите приложение

### Проблема: API ключ не работает

1. Убедитесь, что вы используете SHA-256 хэш ключа в БД
2. Проверьте, что ключ активен (`active = true`)
3. Проверьте заголовок: `X-API-Key` (не `Authorization`)

## Безопасность

⚠️ **ВАЖНО для Production**:

1. **Измените пароль admin** сразу после первого запуска
2. **Используйте HTTPS** (настройте reverse proxy)
3. **Не отключайте аутентификацию** в production
4. **Используйте сильные пароли** для всех пользователей
5. **Регулярно ротируйте API ключи**

## Дополнительная информация

Подробная документация: [AUTHENTICATION.md](./AUTHENTICATION.md)
