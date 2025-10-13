--liquibase formatted sql

--changeset DanielK:3


-- Таблица для kworks
CREATE TABLE IF NOT EXISTS kworks (
                                      kwork_id      BIGINT PRIMARY KEY,
                                      status        VARCHAR(20) NOT NULL,
    updated       TIMESTAMP,
    created       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    kwork_status  TEXT,
    name          TEXT,
    description   TEXT,
    category_id   VARCHAR(100),
    lang          VARCHAR(20),
    price_limit   VARCHAR(50),
    max_days      VARCHAR(20),
    date_create   VARCHAR(50),
    date_active   VARCHAR(50),
    date_expire   VARCHAR(50),
    kwork_user_id BIGINT,
    username      VARCHAR(255),
    is_file_exist BOOLEAN DEFAULT FALSE,
    kwork_count   INT
    );

-- Таблица для urls
CREATE TABLE IF NOT EXISTS urls (
                                    id BIGSERIAL PRIMARY KEY,
                                    url TEXT NOT NULL,
                                    updated TIMESTAMP,
                                    created TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Таблица для связки url <-> kwork
CREATE TABLE IF NOT EXISTS url_kwork (
                                         id BIGSERIAL PRIMARY KEY,
                                         kwork_id BIGINT NOT NULL,
                                         url_id BIGINT NOT NULL,
                                         CONSTRAINT fk_urlkwork_kwork FOREIGN KEY (kwork_id) REFERENCES kworks (kwork_id),
    CONSTRAINT fk_urlkwork_url FOREIGN KEY (url_id) REFERENCES urls (id)
    );

-- Таблица для уведомлений
CREATE TABLE IF NOT EXISTS user_kwork_notification (
                                                       id BIGSERIAL PRIMARY KEY,
                                                       telegram_id BIGINT NOT NULL,
                                                       kwork_id BIGINT NOT NULL,
                                                       status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_notification_kwork FOREIGN KEY (kwork_id) REFERENCES kworks (kwork_id)
    );

-- Таблица для связки user <-> url
CREATE TABLE IF NOT EXISTS user_url (
                                        id BIGSERIAL PRIMARY KEY,
                                        telegram_id BIGINT NOT NULL,
                                        url_id BIGINT NOT NULL,
                                        CONSTRAINT fk_userurl_url FOREIGN KEY (url_id) REFERENCES urls (id)
    );

-- Индексы

-- kworks
CREATE INDEX IF NOT EXISTS idx_kworks_status ON kworks(status);

-- urls
CREATE UNIQUE INDEX IF NOT EXISTS idx_urls_url ON urls(url);

-- url_kwork
CREATE INDEX IF NOT EXISTS idx_urlkwork_kwork_id ON url_kwork(kwork_id);
CREATE INDEX IF NOT EXISTS idx_urlkwork_url_id   ON url_kwork(url_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_urlkwork_pair ON url_kwork(url_id, kwork_id);

-- user_kwork_notification
CREATE INDEX IF NOT EXISTS idx_notifications_telegram_id ON user_kwork_notification(telegram_id);
CREATE INDEX IF NOT EXISTS idx_notifications_kwork_id    ON user_kwork_notification(kwork_id);
CREATE INDEX IF NOT EXISTS idx_notifications_status      ON user_kwork_notification(status);

-- user_url
CREATE INDEX IF NOT EXISTS idx_userurl_telegram_id ON user_url(telegram_id);
CREATE INDEX IF NOT EXISTS idx_userurl_url_id      ON user_url(url_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_userurl_pair ON user_url(telegram_id, url_id);