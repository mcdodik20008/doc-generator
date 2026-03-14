-- Таблица чат-сессий пользователей
-- Хранит историю диалогов с AI ассистентом
CREATE TABLE IF NOT EXISTS doc_generator.chat_session
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES doc_generator.users (id) ON DELETE CASCADE,
    session_id     VARCHAR(36)  NOT NULL UNIQUE,  -- UUID для совместимости с localStorage
    title          VARCHAR(255) NOT NULL,
    messages       JSONB        NOT NULL DEFAULT '[]'::jsonb,  -- История сообщений
    application_id BIGINT REFERENCES doc_generator.application (id) ON DELETE SET NULL,  -- Опциональная привязка к приложению
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Индексы для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_chat_session_user_id ON doc_generator.chat_session (user_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_updated_at ON doc_generator.chat_session (updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_session_session_id ON doc_generator.chat_session (session_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_user_updated ON doc_generator.chat_session (user_id, updated_at DESC);

-- Комментарии для документации
COMMENT ON TABLE doc_generator.chat_session IS 'Чат-сессии пользователей с AI ассистентом';
COMMENT ON COLUMN doc_generator.chat_session.session_id IS 'UUID сессии (для совместимости с localStorage на фронтенде)';
COMMENT ON COLUMN doc_generator.chat_session.messages IS 'История сообщений в формате JSON: [{ role: "user"|"assistant", data: {...}, timestamp: Long }]';
COMMENT ON COLUMN doc_generator.chat_session.application_id IS 'ID приложения, если чат привязан к конкретному приложению';
