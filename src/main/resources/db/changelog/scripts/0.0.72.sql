--liquibase formatted sql

--changeset DanielK:75

-- Поддержка калорийного бота (et-112): заявка из /support — тема в группе поддержки (форуме), переписка через бота.
-- status: DRAFT — категория выбрана, ждём описание; OPEN — в группе; CLOSED — закрыта поддержкой.
-- awaiting_text_since — с этого момента следующее сообщение пользователя (текст или фото) уходит в заявку,
-- а не распознаётся как еда. null — не ждём. Старше calorie.support.input-timeout — не ждём.
CREATE TABLE support_ticket
(
    id                       BIGSERIAL PRIMARY KEY,
    app_user_id              BIGINT      NOT NULL,
    category                 VARCHAR(30) NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    topic_id                 INTEGER,
    awaiting_text_since      TIMESTAMPTZ,
    last_user_message_at     TIMESTAMPTZ,
    last_support_message_at  TIMESTAMPTZ,
    reminded_at              TIMESTAMPTZ,
    rating                   SMALLINT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at                TIMESTAMPTZ
);

CREATE INDEX idx_support_ticket_user ON support_ticket (app_user_id, created_at);
CREATE UNIQUE INDEX idx_support_ticket_topic ON support_ticket (topic_id) WHERE topic_id IS NOT NULL;
CREATE INDEX idx_support_ticket_awaiting ON support_ticket (app_user_id) WHERE awaiting_text_since IS NOT NULL;

-- Переписка по заявке. direction: USER — от пользователя, SUPPORT — от поддержки.
-- user_message_id — id сообщения в чате пользователя: по ответу (reply) на сообщение поддержки находим заявку.
-- text — текст или подпись к фото («[фото]», если подписи нет)
CREATE TABLE support_message
(
    id               BIGSERIAL PRIMARY KEY,
    ticket_id        BIGINT      NOT NULL REFERENCES support_ticket (id),
    direction        VARCHAR(10) NOT NULL,
    text             TEXT,
    user_message_id  INTEGER,
    group_message_id INTEGER,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_support_message_ticket ON support_message (ticket_id, created_at);
