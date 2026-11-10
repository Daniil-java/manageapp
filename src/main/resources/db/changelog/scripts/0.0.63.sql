--liquibase formatted sql
--changeset DanielK:66

-- ════════════════════════════════════════════════════════
--  КАНАЛ: КВИЗЫ И ОПРОСЫ
--  Пост-опрос — обычный channel_post_queue (слот, проверка, автопилот),
--  а содержимое опроса и голоса — в channel_poll.
--  Голоса присылает Telegram (апдейт poll) по опросам, отправленным ботом.
--  На следующий день в poll-results-hour выходит пост с итогами — ответом на опрос.
-- ════════════════════════════════════════════════════════

CREATE TABLE channel_poll (
    id                BIGSERIAL PRIMARY KEY,
    post_queue_id     BIGINT      NOT NULL UNIQUE REFERENCES channel_post_queue (id) ON DELETE CASCADE,
    -- QUIZ — с правильным ответом и пояснением, REGULAR — опрос мнений
    type              VARCHAR(20) NOT NULL,
    question          VARCHAR(300) NOT NULL,
    options           JSONB       NOT NULL,
    correct_option_id INTEGER,
    explanation       VARCHAR(200),
    -- id опроса в Telegram (по нему приходят голоса) и сообщения в канале (на него отвечает пост с итогами)
    tg_poll_id        VARCHAR(64) UNIQUE,
    tg_message_id     INTEGER,
    option_votes      JSONB,
    total_voters      INTEGER     NOT NULL DEFAULT 0,
    published_at      TIMESTAMPTZ,
    results_due_at    TIMESTAMPTZ,
    results_sent_at   TIMESTAMPTZ,
    -- пост с итогами; NULL при отправленных итогах — итоги пропущены (мало голосов)
    results_post_id   BIGINT
);

CREATE INDEX idx_channel_poll_results_due ON channel_poll (results_due_at) WHERE results_sent_at IS NULL;

INSERT INTO channel_topic_category (topic_type, name, daily_slots, is_active, hashtag, autopilot)
VALUES ('QUIZ', 'Квиз', 0, true, NULL, true),
       ('POLL', 'Опрос', 0, true, NULL, true),
       ('QUIZ_ANSWER', 'Итоги квиза и опроса', 0, true, '#итоги', false)
ON CONFLICT (name) DO NOTHING;

-- квиз — ср 14:00, опрос — вс 14:00; итоги — на следующий день в 21:00 (вне слотов)
INSERT INTO channel_schedule_slot (topic_category_id, post_time, is_active, day_of_week)
VALUES ((SELECT id FROM channel_topic_category WHERE topic_type = 'QUIZ' LIMIT 1), '14:00:00', true, 3),
       ((SELECT id FROM channel_topic_category WHERE topic_type = 'POLL' LIMIT 1), '14:00:00', true, 7);
