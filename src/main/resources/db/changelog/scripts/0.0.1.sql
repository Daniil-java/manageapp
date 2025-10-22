--liquibase formatted sql

--changeset DanielK:1


CREATE TABLE IF NOT EXISTS telegram_users (
                                              telegram_id BIGINT NOT NULL PRIMARY KEY,
                                              username TEXT,
                                              firstname TEXT,
                                              lastname TEXT,
                                              language_code TEXT,
                                              bot_identifier TEXT NOT NULL,
                                              response_count   BIGINT DEFAULT 0,
                                              updated TIMESTAMP,
                                              created TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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


