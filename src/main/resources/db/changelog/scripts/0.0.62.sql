--liquibase formatted sql
--changeset DanielK:65

-- ════════════════════════════════════════════════════════
--  КАНАЛ: БАНК ТЕМ РУБРИК
--  Тему поста рубрики («Миф или правда», «Факт») выбирает код из банка,
--  а не модель — так темы не повторяются. Банк пополняет AI, админ
--  выключает лишнее командой /topics.
--  status: NEW — ждёт поста, USED — пост написан, DISABLED — выключена админом.
-- ════════════════════════════════════════════════════════

CREATE TABLE channel_rubric_topic (
    id            BIGSERIAL PRIMARY KEY,
    category_id   BIGINT       NOT NULL REFERENCES channel_topic_category (id) ON DELETE CASCADE,
    section       VARCHAR(64)  NOT NULL,
    text          VARCHAR(300) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    used_at       TIMESTAMPTZ,
    post_queue_id BIGINT,
    created       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_rubric_topic_category_status ON channel_rubric_topic (category_id, status);
