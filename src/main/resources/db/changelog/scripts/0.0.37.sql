--liquibase formatted sql
--changeset DanielK:37

-- 1. Справочник категорий
CREATE TABLE channel_topic_category (
                                        id BIGSERIAL PRIMARY KEY,
                                        topic_type VARCHAR(50) NOT NULL,
                                        name VARCHAR(255) NOT NULL UNIQUE,
                                        daily_slots INTEGER DEFAULT 0,
                                        is_active BOOLEAN DEFAULT TRUE
);

-- 2. Очередь постов
CREATE TABLE channel_post_queue (
                                    id BIGSERIAL PRIMARY KEY,
                                    category_id BIGINT REFERENCES channel_topic_category(id) ON DELETE SET NULL,
                                    topic_id BIGINT,
                                    text_content TEXT,
                                    image_description TEXT,
                                    scheduled_at TIMESTAMP WITH TIME ZONE,
                                    status VARCHAR(50) NOT NULL,
                                    sent_at TIMESTAMP WITH TIME ZONE,
                                    tg_message_id INTEGER,
                                    parent_post_id INTEGER,
                                    source_article_id BIGINT,
                                    created TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Изображения к постам
CREATE TABLE channel_post_image (
                                    id BIGSERIAL PRIMARY KEY,
                                    post_queue_id BIGINT NOT NULL REFERENCES channel_post_queue(id),
                                    image_prompt TEXT,
                                    file_path VARCHAR(500),
                                    source VARCHAR(20) NOT NULL,
                                    status VARCHAR(20) NOT NULL DEFAULT 'GENERATING',
                                    created TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4. Слоты расписания
CREATE TABLE channel_schedule_slot (
                                       id BIGSERIAL PRIMARY KEY,
                                       topic_category_id BIGINT NOT NULL,
                                       post_time TIME NOT NULL,
                                       is_active BOOLEAN DEFAULT TRUE,
                                       CONSTRAINT fk_topic_category FOREIGN KEY (topic_category_id)
                                           REFERENCES channel_topic_category(id) ON DELETE CASCADE
);

-- Индексы
CREATE INDEX idx_post_queue_status_scheduled ON channel_post_queue(status, scheduled_at);
CREATE INDEX idx_post_image_post_queue ON channel_post_image(post_queue_id);
CREATE INDEX idx_topic_category_type_active ON channel_topic_category(topic_type, is_active);
CREATE INDEX idx_schedule_slot_category ON channel_schedule_slot(topic_category_id, is_active);

-- Вставки данных
INSERT INTO channel_topic_category (topic_type, name, daily_slots, is_active)
VALUES ('ARTICLE', 'ARTICLE', 2, true)
    ON CONFLICT (name) DO NOTHING;

INSERT INTO channel_schedule_slot (topic_category_id, post_time, is_active)
VALUES
    ((SELECT id FROM channel_topic_category WHERE name = 'ARTICLE' LIMIT 1), '09:00:00', true),
    ((SELECT id FROM channel_topic_category WHERE name = 'ARTICLE' LIMIT 1), '19:00:00', true);