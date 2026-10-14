--liquibase formatted sql

--changeset DanielK:7

-- Пользователи
CREATE TABLE pomidoro_users (
                                id BIGSERIAL PRIMARY KEY,
                                username VARCHAR(255),
                                password VARCHAR(255),
                                telegram_id BIGINT,
                                chat_id BIGINT,
                                bot_state VARCHAR(50), -- Enum как строка
                                firstname VARCHAR(255),
                                lastname VARCHAR(255),
                                language_code VARCHAR(10),
                                last_updated_task_id BIGINT,
                                last_updated_task_message_id BIGINT,
                                last_updated_timer_settings_message_id BIGINT,
                                created TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Таймеры
CREATE TABLE timers (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        status VARCHAR(50), -- Enum как строка
                        work_duration INT,
                        short_break_duration INT,
                        long_break_duration INT,
                        long_break_interval INT,
                        is_autostart_work BOOLEAN,
                        is_autostart_break BOOLEAN,
                        interval INT,
                        telegram_message_id INT,
                        stop_time TIMESTAMP,
                        minute_to_stop INT,
                        updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_timer_user FOREIGN KEY (user_id)
                            REFERENCES pomidoro_users (id) ON DELETE CASCADE
);

-- Задачи
CREATE TABLE tasks (
                       id BIGSERIAL PRIMARY KEY,
                       user_id BIGINT NOT NULL,
                       parent_id BIGINT,
                       name VARCHAR(255),
                       priority VARCHAR(50), -- Enum как строка
                       status VARCHAR(50),   -- Enum как строка
                       comment TEXT,
                       updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       CONSTRAINT fk_task_user FOREIGN KEY (user_id)
                           REFERENCES pomidoro_users (id) ON DELETE CASCADE,
                       CONSTRAINT fk_task_parent FOREIGN KEY (parent_id)
                           REFERENCES tasks (id) ON DELETE SET NULL
);

-- Связь многие-ко-многим между таймерами и задачами
CREATE TABLE timer_tasks (
                             timer_id BIGINT NOT NULL,
                             task_id BIGINT NOT NULL,
                             PRIMARY KEY (timer_id, task_id),
                             CONSTRAINT fk_tt_timer FOREIGN KEY (timer_id)
                                 REFERENCES timers (id) ON DELETE CASCADE,
                             CONSTRAINT fk_tt_task FOREIGN KEY (task_id)
                                 REFERENCES tasks (id) ON DELETE CASCADE
);