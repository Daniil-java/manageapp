--liquibase formatted sql
--changeset DanielK:67

-- ════════════════════════════════════════════════════════
--  КАНАЛ: ОБРАТНАЯ СВЯЗЬ И «УГАДАЙ КАЛОРИЙНОСТЬ»
--  reactions — сколько реакций у поста в канале (апдейт message_reaction_count);
--  channel_daily_stats — подписчики раз в день, для прироста в утреннем отчёте;
--  рубрика GUESS_CALORIES — фото блюда (Pixabay) + квиз, калории считает распознавание калорийного бота.
-- ════════════════════════════════════════════════════════

ALTER TABLE channel_post_queue ADD COLUMN reactions INTEGER NOT NULL DEFAULT 0;
ALTER TABLE channel_post_queue ADD COLUMN reactions_updated_at TIMESTAMPTZ;
CREATE INDEX idx_post_queue_tg_message ON channel_post_queue (tg_message_id);

CREATE TABLE channel_daily_stats (
    day         DATE PRIMARY KEY,
    subscribers INTEGER     NOT NULL,
    created     TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO channel_topic_category (topic_type, name, daily_slots, is_active, hashtag, autopilot)
VALUES ('GUESS_CALORIES', 'Угадай калорийность', 0, true, '#угадай_калории', true)
ON CONFLICT (name) DO NOTHING;

-- чт 14:00; итоги — в пт 21:00
INSERT INTO channel_schedule_slot (topic_category_id, post_time, is_active, day_of_week)
VALUES ((SELECT id FROM channel_topic_category WHERE topic_type = 'GUESS_CALORIES' LIMIT 1), '14:00:00', true, 4);
