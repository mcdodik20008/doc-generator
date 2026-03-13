--liquibase formatted sql

-- ============================================================
-- Users table for web authentication (form-based login)
-- ============================================================

CREATE TABLE IF NOT EXISTS doc_generator.users (
    id              BIGSERIAL       PRIMARY KEY,
    username        TEXT            NOT NULL UNIQUE,
    password_hash   TEXT            NOT NULL,
    email           TEXT,
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    roles           TEXT[]          NOT NULL DEFAULT '{USER}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);

-- Индекс для быстрого поиска по username (используется при логине)
CREATE INDEX IF NOT EXISTS idx_users_username
    ON doc_generator.users (username);

-- Индекс для фильтрации активных пользователей
CREATE INDEX IF NOT EXISTS idx_users_enabled
    ON doc_generator.users (enabled) WHERE enabled = true;

-- ============================================================
-- Начальные данные: создаем администратора по умолчанию
-- username: admin
-- password: admin123!@# (BCrypt hash with strength 10)
-- ============================================================
INSERT INTO doc_generator.users (username, password_hash, email, enabled, roles, created_at)
VALUES (
    'admin',
    '$2a$10$01F8E1tDlM0/hhp2/DuH.eA8ATJ93ZG/MLxdnC6rYhHU4gr8L231e', -- BCrypt hash для "admin123!@#"
    'admin@docgen.local',
    true,
    '{ADMIN,USER}',
    now()
)
ON CONFLICT (username) DO NOTHING;

-- Комментарии к таблице
COMMENT ON TABLE doc_generator.users IS 'Пользователи системы для веб-аутентификации';
COMMENT ON COLUMN doc_generator.users.username IS 'Уникальное имя пользователя для логина';
COMMENT ON COLUMN doc_generator.users.password_hash IS 'BCrypt хэш пароля (strength 10)';
COMMENT ON COLUMN doc_generator.users.roles IS 'Роли пользователя: ADMIN, USER, etc.';
COMMENT ON COLUMN doc_generator.users.enabled IS 'Флаг активности аккаунта';
COMMENT ON COLUMN doc_generator.users.last_login_at IS 'Время последнего успешного входа';
