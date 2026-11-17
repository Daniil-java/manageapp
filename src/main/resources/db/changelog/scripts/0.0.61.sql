--liquibase formatted sql
--changeset DanielK:64

-- ════════════════════════════════════════════════════════
--  КАНАЛ: РУБРИКИ ПО ДНЯМ НЕДЕЛИ
--  Рубрика = channel_topic_category, её слоты — channel_schedule_slot.
--  day_of_week (ISO: 1 — пн … 7 — вс), NULL — каждый день.
--  hashtag — дописывается в конец поста; autopilot — посты рубрики
--  автопилот ставит в очередь без оценки AI (у рубрик нет источника и оценки).
-- ════════════════════════════════════════════════════════

ALTER TABLE channel_topic_category ADD COLUMN hashtag VARCHAR(64);
ALTER TABLE channel_topic_category ADD COLUMN autopilot BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE channel_schedule_slot ADD COLUMN day_of_week INTEGER;
ALTER TABLE channel_schedule_slot ADD CONSTRAINT chk_schedule_slot_day_of_week
    CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7);

-- name теперь подпись рубрики для админа (/q, превью); код ищет категорию по topic_type
UPDATE channel_topic_category
SET name = 'Научная новость', hashtag = '#наука'
WHERE topic_type = 'ARTICLE';

INSERT INTO channel_topic_category (topic_type, name, daily_slots, is_active, hashtag, autopilot)
VALUES ('MYTH', 'Миф или правда', 0, true, '#миф_или_правда', true),
       ('FACT', 'Факт', 0, true, '#факт', true)
ON CONFLICT (name) DO NOTHING;

-- миф — пн и пт, факт — вт и сб, в 14:00 (между постами 09:00 и 19:00).
-- ср, чт и вс оставлены под квизы и итоги недели.
INSERT INTO channel_schedule_slot (topic_category_id, post_time, is_active, day_of_week)
VALUES ((SELECT id FROM channel_topic_category WHERE topic_type = 'MYTH' LIMIT 1), '14:00:00', true, 1),
       ((SELECT id FROM channel_topic_category WHERE topic_type = 'MYTH' LIMIT 1), '14:00:00', true, 5),
       ((SELECT id FROM channel_topic_category WHERE topic_type = 'FACT' LIMIT 1), '14:00:00', true, 2),
       ((SELECT id FROM channel_topic_category WHERE topic_type = 'FACT' LIMIT 1), '14:00:00', true, 6);
