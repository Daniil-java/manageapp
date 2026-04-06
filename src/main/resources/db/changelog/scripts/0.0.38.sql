--liquibase formatted sql
--changeset DanielK:38

-- 1. Очистка таблицы channel_post_queue
ALTER TABLE channel_post_queue
DROP COLUMN topic_id,
    DROP COLUMN source_article_id;

-- 2. Добавление заголовка в очередь
ALTER TABLE channel_post_queue
    ADD COLUMN title TEXT;

-- 3. Удаление промпта из таблицы изображений
ALTER TABLE channel_post_image
DROP COLUMN image_prompt;
