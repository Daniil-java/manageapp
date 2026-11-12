--liquibase formatted sql

--changeset DanielK:73

-- Картинка поста по умолчанию — фото с Pixabay (et-109): ключевые слова от AI (точные и запасные, пошире)
-- и id фото Pixabay у поста. id остаётся и после публикации — по нему фото не повторяются в канале
ALTER TABLE channel_post_queue
    ADD COLUMN image_query       TEXT,
    ADD COLUMN image_query_broad TEXT,
    ADD COLUMN stock_photo_id    BIGINT;

CREATE INDEX idx_post_queue_stock_photo ON channel_post_queue (stock_photo_id) WHERE stock_photo_id IS NOT NULL;

-- Автор фото со стока — для подписи «Фото: автор / Pixabay»
ALTER TABLE channel_post_image
    ADD COLUMN stock_author   TEXT,
    ADD COLUMN stock_page_url TEXT;
