--liquibase formatted sql

--changeset DanielK:8

CREATE TABLE IF NOT EXISTS hh_skill (
                                        id              BIGSERIAL PRIMARY KEY,
                                        skill_name      TEXT    NOT NULL,
                                        count           BIGINT  NOT NULL DEFAULT 0,
                                        skill_source    TEXT    NOT NULL,
    CONSTRAINT uq_hh_skill_unique        UNIQUE (skill_name, skill_source)
    );