--liquibase formatted sql
--changeset DanielK:68

-- Откуда пришёл пользователь: первая UTM-ссылка, по которой он впервые запустил бота.
-- Одна строка на пользователя. Старые пользователи, перешедшие по ссылке позже, сюда не попадают — только в utm_clicks.
CREATE TABLE IF NOT EXISTS utm_attributions (
    app_user_id          BIGINT PRIMARY KEY,
    utm_link_id          BIGINT NOT NULL REFERENCES utm_links (id),
    referrer_app_user_id BIGINT,          -- кто пригласил (владелец реферальной ссылки ref<id>), иначе NULL
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_utm_attributions_utm_link_id ON utm_attributions (utm_link_id);
CREATE INDEX IF NOT EXISTS idx_utm_attributions_referrer
    ON utm_attributions (referrer_app_user_id) WHERE referrer_app_user_id IS NOT NULL;

-- Отчёты по ссылке считают клики по utm_link_id
CREATE INDEX IF NOT EXISTS idx_utm_clicks_utm_link_id ON utm_clicks (utm_link_id);
