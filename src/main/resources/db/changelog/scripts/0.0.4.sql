--liquibase formatted sql

--changeset DanielK:4

CREATE TABLE IF NOT EXISTS flights (
                         id SERIAL PRIMARY KEY,
                         flight_code VARCHAR(50) NOT NULL,
                         number VARCHAR(50) NOT NULL,
                         airline VARCHAR(100),
                         departure_airport VARCHAR(100),
                         arrival_airport VARCHAR(100),
                         scheduled_departure VARCHAR(50),
                         scheduled_arrival VARCHAR(50),
                         actual_arrival VARCHAR(50),
                         status VARCHAR(50),
                         terminal VARCHAR(50),
                         gate VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS user_flights (
                                            id SERIAL NOT NULL PRIMARY KEY,
                                            telegram_id BIGINT NOT NULL,         -- просто хранится ID, без ссылки
                                            flight_id BIGINT NOT NULL,
                                            subscribed_at TIMESTAMP,
                                            notifications_enabled BOOLEAN,

                                            CONSTRAINT fk_user_flights_flight
                                            FOREIGN KEY (flight_id) REFERENCES flights(id)
    ON DELETE CASCADE
    );

-- индексы для быстрого поиска
CREATE INDEX idx_user_flights_user ON user_flights(telegram_id);
CREATE INDEX idx_user_flights_flight ON user_flights(flight_id);