--liquibase formatted sql

--changeset DanielK:12

CREATE TABLE generation_balance (
                                    id BIGSERIAL PRIMARY KEY,
                                    telegram_id BIGINT NOT NULL,
                                    generation_requests BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_generation_balance_telegram_id ON generation_balance (telegram_id);

CREATE INDEX idx_generation_balance_telegram_id ON generation_balance (telegram_id);


CREATE TABLE payments (
                          id BIGSERIAL PRIMARY KEY,
                          telegram_id BIGINT NOT NULL,
                          provider VARCHAR(20) NOT NULL,
                          provider_payment_id TEXT,
                          payload TEXT NOT NULL,
                          telegram_invoice_payload TEXT,
                          description TEXT,
                          currency VARCHAR(10) NOT NULL,
                          amount INTEGER NOT NULL CHECK (amount >= 0),
                          stars_amount INTEGER NOT NULL DEFAULT 0 CHECK (stars_amount >= 0),
                          status TEXT NOT NULL,
                          provider_status TEXT,
                          created_at TIMESTAMP NOT NULL DEFAULT now(),
                          paid_at TIMESTAMP,
                          canceled_at TIMESTAMP
);

-- Уникальность provider_payment_id только если не NULL
CREATE UNIQUE INDEX ux_payments_provider_payment_id_notnull
    ON payments (provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

-- Уникальность telegram_invoice_payload только если не NULL
CREATE UNIQUE INDEX ux_payments_telegram_invoice_payload_notnull
    ON payments (telegram_invoice_payload)
    WHERE telegram_invoice_payload IS NOT NULL;

-- Полезные индексы
CREATE INDEX idx_payments_telegram_id
    ON payments (telegram_id);

CREATE INDEX idx_payments_provider_payment_id
    ON payments (provider_payment_id);

CREATE TABLE webhook_events (
                                id BIGSERIAL PRIMARY KEY,
                                provider TEXT NOT NULL,
                                event TEXT NOT NULL,
                                object_id TEXT,
                                remote_addr TEXT,
                                processed BOOLEAN NOT NULL DEFAULT FALSE,
                                error TEXT,
                                received_at TIMESTAMP NOT NULL DEFAULT now(),
                                processed_at TIMESTAMP,
                                payload TEXT
);

-- Индексы для быстрого поиска/дедупликации
CREATE INDEX idx_webhook_events_lookup
    ON webhook_events (provider, event, object_id);

CREATE INDEX idx_webhook_events_received_at
    ON webhook_events (received_at);

-- (по желанию) отдельный индекс по processed=true
CREATE INDEX idx_webhook_events_processed_true
    ON webhook_events (provider, event, object_id)
    WHERE processed = TRUE;
