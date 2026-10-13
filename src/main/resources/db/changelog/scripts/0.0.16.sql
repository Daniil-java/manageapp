--liquibase formatted sql

--changeset DanielK:16

CREATE TABLE user_subscription (
                                   id              BIGSERIAL PRIMARY KEY,
                                   telegram_id     BIGINT          NOT NULL,
                                   pricing_plan_id BIGINT          NOT NULL,
                                   payment_id      BIGINT          NOT NULL,

                                   status          VARCHAR(32)     NOT NULL,

                                   start_at        TIMESTAMP NOT NULL,
                                   end_at          TIMESTAMP NOT NULL,
                                   created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);