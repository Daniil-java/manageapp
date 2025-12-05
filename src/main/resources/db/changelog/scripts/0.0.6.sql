--liquibase formatted sql

--changeset DanielK:5

-- Пользователи
CREATE TABLE hh_user_info (
                              id BIGSERIAL PRIMARY KEY,
                              telegram_id BIGINT,
                              info TEXT,
                              created TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Фильтры
CREATE TABLE work_filters (
                              id BIGSERIAL PRIMARY KEY,
                              hh_user_info_id BIGINT,
                              url TEXT,
                              created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT fk_work_filters_user
                                  FOREIGN KEY (hh_user_info_id) REFERENCES hh_user_info (id)
                                      ON DELETE CASCADE
);

-- Вакансии
CREATE TABLE vacancies (
                           id BIGSERIAL PRIMARY KEY,
                           hh_id BIGINT,
                           url TEXT,
                           name TEXT,
                           area TEXT,
                           department TEXT,
                           description TEXT,
                           generated_description TEXT,
                           employment TEXT,
                           experience TEXT,
                           key_skills TEXT,
                           salary TEXT,
                           schedule TEXT,
                           notification_attempt_count INT NOT NULL DEFAULT 0,
                           work_filter_id BIGINT,
                           status VARCHAR(50),
                           employer_description TEXT,
                           created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_vacancies_work_filter
                               FOREIGN KEY (work_filter_id) REFERENCES work_filters (id)
                                   ON DELETE CASCADE
);
