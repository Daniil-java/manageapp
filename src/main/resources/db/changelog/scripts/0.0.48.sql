--liquibase formatted sql
--changeset DanielK:48

-- ════════════════════════════════════════════════════════
--  РОЛИ
--  Таблица roles + связующая app_user_roles
-- ════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS roles (
                                     id          BIGSERIAL    PRIMARY KEY,
                                     role_name   VARCHAR(50)  NOT NULL UNIQUE,   -- хранит RoleName enum: ROLE_USER, ROLE_ADMIN
    description VARCHAR(255)
    );

-- Связующая таблица (ManyToMany из AppUser.roles)
CREATE TABLE IF NOT EXISTS app_user_roles (
    app_user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role_id     BIGINT NOT NULL REFERENCES roles(id)     ON DELETE CASCADE,
    PRIMARY KEY (app_user_id, role_id)
    );

CREATE INDEX IF NOT EXISTS idx_app_user_roles_role_id     ON app_user_roles (role_id);
CREATE INDEX IF NOT EXISTS idx_app_user_roles_app_user_id ON app_user_roles (app_user_id);

-- ── Seed: минимально необходимые роли ──────────────────
INSERT INTO roles (role_name, description) VALUES
                                               ('ROLE_USER',  'Стандартный пользователь приложения'),
                                               ('ROLE_ADMIN', 'Администратор с полным доступом')
    ON CONFLICT (role_name) DO NOTHING;

-- ── Назначаем ROLE_USER всем существующим app_users ────
-- Безопасно: ON CONFLICT игнорирует, если уже есть
INSERT INTO app_user_roles (app_user_id, role_id)
SELECT au.id, r.id
FROM   app_users au
           CROSS  JOIN roles r
WHERE  r.role_name = 'ROLE_USER'
    ON CONFLICT DO NOTHING;

-- ROLLBACK
-- rollback DELETE FROM app_user_roles;
-- rollback DROP INDEX IF EXISTS idx_app_user_roles_role_id;
-- rollback DROP INDEX IF EXISTS idx_app_user_roles_app_user_id;
-- rollback DROP TABLE IF EXISTS app_user_roles;
-- rollback DROP TABLE IF EXISTS roles;


--liquibase formatted sql
--changeset DanielK:49

-- ════════════════════════════════════════════════════════
--  МУЛЬТИПРОВАЙДЕРНАЯ АУТЕНТИФИКАЦИЯ
--  Сейчас нигде не используется, но entity уже есть —
--  создаём структуру заранее, пока в БД данных нет.
--
--  provider_id — внешний идентификатор:
--    EMAIL    → email-адрес
--    TELEGRAM → telegram_id (строкой)
--    GOOGLE   → google sub
--    APPLE    → apple sub
-- ════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS user_auth_identities (
                                                    id            BIGSERIAL    PRIMARY KEY,
                                                    app_user_id   BIGINT       NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    provider      VARCHAR(50)  NOT NULL,                    -- AuthProvider enum
    provider_id   VARCHAR(255) NOT NULL,                    -- уникален в рамках провайдера
    password_hash VARCHAR(255),                             -- только для EMAIL
    email         VARCHAR(255),                             -- может отличаться от app_users.email
    verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP
    );

-- Одна запись на связку провайдер + внешний id
CREATE UNIQUE INDEX IF NOT EXISTS ux_auth_identity_provider_provider_id
    ON user_auth_identities (provider, provider_id);

-- Быстрый поиск всех способов входа конкретного пользователя
CREATE INDEX IF NOT EXISTS idx_auth_identity_app_user_id
    ON user_auth_identities (app_user_id);

-- ── Преднаполнение из app_users для EMAIL-провайдера ───
-- Переливаем тех, у кого уже есть email + пароль
-- (то есть зарегистрированных через /auth/register)
INSERT INTO user_auth_identities (app_user_id, provider, provider_id, password_hash, email, verified, created_at)
SELECT
    au.id,
    'EMAIL'          AS provider,
    au.email         AS provider_id,   -- для EMAIL провайдер-id = email
    au.password_hash,
    au.email,
    TRUE             AS verified,      -- считаем верифицированными, раз уже в системе
    COALESCE(au.created_at, NOW())
FROM app_users au
WHERE au.email        IS NOT NULL
  AND au.password_hash IS NOT NULL
    ON CONFLICT (provider, provider_id) DO NOTHING;

-- ── Преднаполнение из telegram_users для TELEGRAM-провайдера ──
-- У части app_users нет email — они пришли из телеги.
-- Берём их из связки telegram_users → app_users.
INSERT INTO user_auth_identities (app_user_id, provider, provider_id, email, verified, created_at)
SELECT DISTINCT ON (tu.app_user_id)
    tu.app_user_id,
    'TELEGRAM'                    AS provider,
    tu.telegram_id::VARCHAR(255)  AS provider_id,
    au.email,                                      -- может быть NULL — это нормально
    TRUE                          AS verified,
    COALESCE(tu.created, NOW())
FROM telegram_users tu
    JOIN app_users au ON au.id = tu.app_user_id
WHERE tu.app_user_id IS NOT NULL
ORDER BY tu.app_user_id, tu.created DESC
ON CONFLICT (provider, provider_id) DO NOTHING;

-- ROLLBACK
-- rollback DROP INDEX IF EXISTS ux_auth_identity_provider_provider_id;
-- rollback DROP INDEX IF EXISTS idx_auth_identity_app_user_id;
-- rollback DROP TABLE IF EXISTS user_auth_identities;