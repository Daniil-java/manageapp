--liquibase formatted sql
--changeset DanielK:60

-- ════════════════════════════════════════════════════════
--  ЯЗЫК ПОЛЬЗОВАТЕЛЯ (бот, рассылки, ИИ)
--  Код языка: ru, en… NULL — пользователь язык не выбирал, берётся язык по умолчанию
--  (calorie.i18n.default-language). Пока выбирать могут только разработчики (/lang).
-- ════════════════════════════════════════════════════════

ALTER TABLE user_settings ADD COLUMN language VARCHAR(10);
