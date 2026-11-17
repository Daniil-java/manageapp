--liquibase formatted sql

--changeset DanielK:1


CREATE TABLE IF NOT EXISTS telegram_users (
                                              id BIGSERIAL PRIMARY KEY,
                                              telegram_id BIGINT NOT NULL,
                                              username TEXT,
                                              firstname TEXT,
                                              lastname TEXT,
                                              language_code TEXT,
                                              bot_identifier TEXT NOT NULL,
                                              response_count   BIGINT DEFAULT 0,
                                              updated TIMESTAMP,
                                              created TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_telegram_users__bot_tg
    ON telegram_users (bot_identifier, telegram_id);

CREATE INDEX IF NOT EXISTS ix_telegram_users__tg
    ON telegram_users (telegram_id);

CREATE INDEX IF NOT EXISTS ix_telegram_users__bot
    ON telegram_users (bot_identifier);

CREATE TABLE IF NOT EXISTS dishes (

                                            id SERIAL NOT NULL PRIMARY KEY,
                                            name TEXT,
                                            calories INT,
                                            proteins INT,
                                            fats INT,
                                            carbohydrates INT,
                                            user_id BIGINT NOT NULL,
                                            created TIMESTAMP
);


