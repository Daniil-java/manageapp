--liquibase formatted sql
--changeset DanielK:41

ALTER TABLE channel_post_image
    ADD COLUMN tg_file_id TEXT;