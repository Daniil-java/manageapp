--liquibase formatted sql
--changeset DanielK:39

CREATE TABLE subreddit (
                           id BIGSERIAL PRIMARY KEY,
                           name TEXT NOT NULL UNIQUE,
                           url TEXT NOT NULL UNIQUE,
                           active BOOLEAN NOT NULL DEFAULT TRUE,
                           created TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE reddit_post (
                             id BIGSERIAL PRIMARY KEY,
                             reddit_id TEXT UNIQUE,
                             subreddit_id BIGINT NOT NULL,
                             url TEXT,
                             title TEXT,
                             content TEXT,
                             content_type TEXT,
                             author TEXT,
                             score INTEGER,
                             comments_count INTEGER,

                             post_created TIMESTAMP WITH TIME ZONE,
                             parsed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                             status TEXT
);

-- FK
ALTER TABLE reddit_post
    ADD CONSTRAINT fk_post_subreddit
        FOREIGN KEY (subreddit_id)
            REFERENCES subreddit(id)
            ON DELETE CASCADE;

-- Индексы

-- дедупликация (ключевой)
CREATE UNIQUE INDEX idx_post_reddit_id ON reddit_post(reddit_id);

-- для шедулеров (по статусу)
CREATE INDEX idx_post_status ON reddit_post(status);

-- выборка по сабреддиту
CREATE INDEX idx_post_subreddit_id ON reddit_post(subreddit_id);

-- сортировки/время
CREATE INDEX idx_post_created ON reddit_post(post_created);

CREATE INDEX idx_post_parsed ON reddit_post(parsed_at);
