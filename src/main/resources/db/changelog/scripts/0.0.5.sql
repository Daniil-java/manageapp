--liquibase formatted sql

--changeset DanielK:5


-- Таблица объектов бронирования
CREATE TABLE IF NOT EXISTS booking_objects (
                                               id BIGSERIAL PRIMARY KEY,
                                               name TEXT NOT NULL,
                                               description TEXT,
                                               capacity INT,
                                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                               updated_at TIMESTAMP
);

-- Таблица правил доступности
CREATE TABLE IF NOT EXISTS rules (
                                     id BIGSERIAL PRIMARY KEY,
                                     booking_object_id BIGINT NOT NULL REFERENCES booking_objects(id) ON DELETE CASCADE,
    day_of_week VARCHAR(16),         -- MONDAY, TUESDAY и т.д.
    specific_date DATE,
    start_time TIME,
    end_time TIME,
    slot_duration_minutes INT,
    working BOOLEAN NOT NULL DEFAULT TRUE
    );

-- Таблица бронирований
CREATE TABLE IF NOT EXISTS bookings (
    id BIGSERIAL PRIMARY KEY,
    booking_object_id BIGINT NOT NULL REFERENCES booking_objects(id) ON DELETE CASCADE,
    telegram_user_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
    );

-- Таблица состояний диалога
CREATE TABLE IF NOT EXISTS states (
    telegram_id BIGINT NOT NULL PRIMARY KEY,
    step TEXT NOT NULL,
    updated_at TIMESTAMP
    );

-- Таблица ответов на форму
CREATE TABLE IF NOT EXISTS forms (
                                     id BIGSERIAL PRIMARY KEY,
                                     booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
    question TEXT,
    answer TEXT
    );

-- Индексы для rules
CREATE INDEX IF NOT EXISTS idx_rules_booking_object_id ON rules (booking_object_id);
CREATE INDEX IF NOT EXISTS idx_rules_day_of_week ON rules (day_of_week);
CREATE INDEX IF NOT EXISTS idx_rules_specific_date ON rules (specific_date);

-- Индексы для bookings
CREATE INDEX IF NOT EXISTS idx_bookings_booking_object_id ON bookings (booking_object_id);
CREATE INDEX IF NOT EXISTS idx_bookings_telegram_user_id ON bookings (telegram_user_id);
CREATE INDEX IF NOT EXISTS idx_bookings_start_time ON bookings (start_time);
CREATE INDEX IF NOT EXISTS idx_bookings_end_time ON bookings (end_time);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings (status);


-- Дефолтные объекты бронирования
INSERT INTO booking_objects (name, description, capacity)
VALUES
    ('Переговорка А', 'Маленькая переговорная комната', 6),
    ('Переговорка B', 'Средняя переговорная комната', 10),
    ('Переговорка C', 'Большая переговорная комната с проектором', 16),
    ('Кабинет директора', 'Индивидуальный кабинет для встреч', 4),
    ('Кабинет HR', 'Кабинет для собеседований и встреч с HR', 3),
    ('Коворкинг-зона', 'Открытое пространство для совместной работы', 20),
    ('Конференц-зал 1', 'Большой зал для презентаций', 50),
    ('Конференц-зал 2', 'Зал для тренингов и семинаров', 40),
    ('Учебный класс', 'Класс для обучения сотрудников', 25),
    ('Лаунж-зона', 'Неформальная зона для встреч и отдыха', 12);

-- Рабочие дни (понедельник–пятница, 09:00–18:00, шаг 60 минут)
INSERT INTO rules (booking_object_id, day_of_week, start_time, end_time, slot_duration_minutes, working)
SELECT id, day_of_week, '09:00', '18:00', 60, TRUE
FROM booking_objects,
     (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'), ('FRIDAY')) AS days(day_of_week);

-- Нерабочие дни (суббота и воскресенье)
INSERT INTO rules (booking_object_id, day_of_week, working)
SELECT id, day_of_week, FALSE
FROM booking_objects,
     (VALUES ('SATURDAY'), ('SUNDAY')) AS days(day_of_week);



