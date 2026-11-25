--liquibase formatted sql

--changeset DanielK:14

CREATE TABLE generation_balance_operations (
                                               id BIGSERIAL PRIMARY KEY,
                                               telegram_id BIGINT NOT NULL,
                                               type VARCHAR(32) NOT NULL,
                                               source VARCHAR(32) NOT NULL,
                                               payment_id BIGINT,
                                               comment TEXT,
                                               request_count BIGINT NOT NULL,
                                               created_at TIMESTAMP
);

-- индекс по пользователю
CREATE INDEX idx_generation_balance_operations_telegram_id
    ON generation_balance_operations (telegram_id);

-- защита от двойного начисления по одному и тому же платежу
-- (для одного source + payment_id + type будет только одна запись)
CREATE UNIQUE INDEX ux_generation_balance_operations_payment
    ON generation_balance_operations (source, payment_id, type);
