--liquibase formatted sql
--changeset DanielK:51

-- ════════════════════════════════════════════════════════
--  TELEGRAM-IDENTITY ДЛЯ ПОЛЬЗОВАТЕЛЕЙ БОТОВ
--  0.0.48 заполнил user_auth_identities разово, а новые пользователи
--  ботов до сих пор получали только telegram_users. Теперь по identity
--  ищется аккаунт при входе на сайте через Telegram — дозаполняем.
--  Безопасно гонять повторно: ON CONFLICT DO NOTHING.
-- ════════════════════════════════════════════════════════

INSERT INTO user_auth_identities (app_user_id, provider, provider_id, email, verified, created_at)
SELECT DISTINCT ON (tu.telegram_id)
    tu.app_user_id,
    'TELEGRAM'                    AS provider,
    tu.telegram_id::VARCHAR(255)  AS provider_id,
    au.email,
    TRUE                          AS verified,
    COALESCE(tu.created, NOW())
FROM telegram_users tu
    JOIN app_users au ON au.id = tu.app_user_id
WHERE tu.app_user_id IS NOT NULL
  AND tu.telegram_id IS NOT NULL
ORDER BY tu.telegram_id, tu.created
ON CONFLICT (provider, provider_id) DO NOTHING;

-- БЛОК ОТКАТА (ROLLBACK)
-- Строки неотличимы от созданных приложением — откатывать нечего.
-- rollback SELECT 1;
