# Аутентификация и Авторизация

## Обзор

Doc-Generator поддерживает два типа аутентификации:

1. **Form-based Login** — для веб-интерфейса (страницы `/`, `/graph`, `/chat`, etc.)
2. **API Key Authentication** — для REST API эндпоинтов (`/api/**`)

## Веб-аутентификация (Form Login)

### Вход в систему

1. Откройте приложение в браузере: `http://localhost:8080`
2. Вы будете перенаправлены на страницу логина: `/login`
3. Введите учетные данные:
   - **Username**: `admin`
   - **Password**: `admin123!@#`
4. После успешного входа вы будете перенаправлены на главную страницу

### Выход из системы

- Перейдите на `/logout` или используйте кнопку выхода (если добавлена в UI)
- После выхода вы будете перенаправлены на страницу логина с сообщением об успешном выходе

### Управление пользователями

#### Добавление нового пользователя

Подключитесь к PostgreSQL и выполните:

```sql
-- Генерация BCrypt хэша пароля (используйте онлайн-генератор или код ниже)
-- Пример для пароля "mypassword":
INSERT INTO doc_generator.users (username, password_hash, email, enabled, roles)
VALUES (
    'newuser',
    '',  -- Замените на реальный BCrypt хэш
    'newuser@example.com',
    true,
    '{USER}'  -- Или '{ADMIN,USER}' для администратора
);
```

#### Генерация BCrypt хэша

Используйте Kotlin код для генерации хэша:

```kotlin
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

fun main() {
    val encoder = BCryptPasswordEncoder()
    val hash = encoder.encode("yourpassword")
    println("BCrypt hash: $hash")
}
```

Или используйте онлайн-сервис: https://bcrypt-generator.com/ (strength: 10)

#### Изменение пароля

```sql
UPDATE doc_generator.users
SET password_hash = ''
WHERE username = 'admin';
```

#### Отключение пользователя

```sql
UPDATE doc_generator.users
SET enabled = false
WHERE username = 'someuser';
```

#### Изменение ролей

```sql
-- Сделать пользователя администратором
UPDATE doc_generator.users
SET roles = '{ADMIN,USER}'
WHERE username = 'someuser';

-- Убрать права администратора
UPDATE doc_generator.users
SET roles = '{USER}'
WHERE username = 'someuser';
```

## API Key Authentication

API ключи используются для доступа к REST API (`/api/**`).

### Создание API ключа

```sql
-- Генерируем случайный ключ (в реальности используйте криптостойкий генератор)
-- Пример ключа: ""
-- SHA-256 хэш можно получить через: echo -n "sk_live_..." | sha256sum

INSERT INTO doc_generator.api_key (name, key_hash, scopes, active)
VALUES (
    'my-api-key',
    'SHA256_HASH_OF_YOUR_KEY',  -- Замените на реальный SHA-256 хэш
    '{read,write}',
    true
);
```

### Использование API ключа

Добавьте заголовок `X-API-Key` к запросам:

```bash
curl -H "X-API-Key: " \
     http://localhost:8080/api/applications
```

## Конфигурация Security

### Включение/Отключение аутентификации

В `application.yml`:

```yaml
docgen:
  security:
    enabled: true  # false для отключения (только для development!)
```

Или через переменную окружения:

```bash
DOCGEN_SECURITY_ENABLED=true ./gradlew.bat bootRun
```

### Защищенные и публичные эндпоинты

#### Публичные (без аутентификации):
- `/login`, `/logout` — страницы аутентификации
- `/static/**`, `/css/**`, `/js/**` — статические ресурсы
- `/swagger/**`, `/v3/api-docs/**` — документация API
- `/actuator/**` — мониторинг и метрики

#### Защищенные (требуют аутентификации):
- `/`, `/graph`, `/chat`, `/dashboard`, `/ingest` — веб-страницы (form login)
- `/api/**` — REST API (API key)

## Роли и Разрешения

### Встроенные роли

- **USER** — базовая роль, доступ к просмотру
- **ADMIN** — администратор, полный доступ

### Проверка ролей в коде

```kotlin
// В контроллере
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/users")
fun getUsers(): List<User> { ... }

// Программная проверка
val authentication = SecurityContextHolder.getContext().authentication
if (authentication.authorities.any { it.authority == "ROLE_ADMIN" }) {
    // Администратор
}
```

## Безопасность

### Рекомендации для Production

1. **Измените пароль администратора по умолчанию**:
   ```sql
   UPDATE doc_generator.users
   SET password_hash = '$2a$10$YourNewStrongPasswordHash'
   WHERE username = 'admin';
   ```

2. **Используйте HTTPS** в production (настройте reverse proxy: nginx, Caddy)

3. **Включите CSRF защиту** для веб-форм (сейчас отключена):
   ```kotlin
   // В SecurityConfig.kt
   .csrf { csrf ->
       csrf.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
   }
   ```

4. **Ограничьте количество попыток входа** (добавьте rate limiting)

5. **Используйте сильные пароли** для всех пользователей

6. **Регулярно ротируйте API ключи**

7. **Храните API ключи в secrets manager** (HashiCorp Vault, AWS Secrets Manager)

## Миграция данных

База данных содержит миграцию Liquibase:
- `migration/19_users.sql` — создание таблицы пользователей
- Автоматически создается пользователь `admin/admin` при первом запуске

## Отладка

### Проверка текущего пользователя

```kotlin
import org.springframework.security.core.context.ReactiveSecurityContextHolder

ReactiveSecurityContextHolder.getContext()
    .subscribe { context ->
        val username = context.authentication.name
        val authorities = context.authentication.authorities
        println("User: $username, Roles: $authorities")
    }
```

### Логирование аутентификации

Включите DEBUG логирование в `application.yml`:

```yaml
logging:
  level:
    com.bftcom.docgenerator.config.CustomUserDetailsService: DEBUG
    com.bftcom.docgenerator.config.ApiKeyAuthenticationFilter: DEBUG
    org.springframework.security: DEBUG
```

## FAQ

**Q: Как сбросить пароль?**
A: Обновите `password_hash` в таблице `users` через SQL (см. "Изменение пароля" выше)

**Q: Можно ли отключить аутентификацию для development?**
A: Да, установите `docgen.security.enabled=false` в `application.yml`

**Q: Как добавить OAuth2/LDAP аутентификацию?**
A: Добавьте соответствующий Spring Security starter и настройте дополнительный AuthenticationManager

**Q: API ключи хранятся в открытом виде?**
A: Нет, хранятся только SHA-256 хэши ключей

**Q: Где посмотреть историю входов?**
A: Поле `last_login_at` в таблице `users` обновляется при каждом успешном входе
