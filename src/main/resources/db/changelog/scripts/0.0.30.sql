--liquibase formatted sql

--changeset DanielK:30

CREATE TABLE metrics_ai_interaction_record (
                                               id BIGSERIAL PRIMARY KEY,

                                               provider_variant VARCHAR(50) NOT NULL,
                                               bot_identifier VARCHAR(100) NOT NULL,

                                               request TEXT,
                                               request_message_type VARCHAR(20),

                                               response TEXT,
                                               response_message_type VARCHAR(20),

                                               input_tokens BIGINT,
                                               output_tokens BIGINT,

                                               created TIMESTAMPTZ DEFAULT NOW()
);
