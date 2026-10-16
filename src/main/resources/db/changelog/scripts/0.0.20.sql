--liquibase formatted sql

--changeset DanielK:20

ALTER TABLE vacancies
    ADD CONSTRAINT uq_vacancy_hh_workfilter UNIQUE (hh_id, work_filter_id);