--liquibase formatted sql
--changeset DanielK:59

-- ════════════════════════════════════════════════════════
--  CHANNEL POSTER: ИСТОЧНИКИ КОНТЕНТА (RSS, PubMed, Reddit)
--  Вместо одного Reddit-парсера — общая таблица источников и найденных материалов.
--  Старые таблицы subreddit / reddit_post не трогаем (больше не используются).
-- ════════════════════════════════════════════════════════

CREATE TABLE channel_content_source (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(20)  NOT NULL,          -- RSS / PUBMED / REDDIT
    name            TEXT         NOT NULL,
    address         TEXT         NOT NULL,          -- RSS: ссылка на ленту, PUBMED: поисковый запрос, REDDIT: имя сабреддита
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    max_items       INTEGER      NOT NULL DEFAULT 15, -- сколько свежих материалов брать за один сбор
    last_fetched_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_error      TEXT,
    fail_count      INTEGER      NOT NULL DEFAULT 0,
    created         TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_content_source_type_address UNIQUE (type, address)
);

CREATE TABLE channel_source_item (
    id           BIGSERIAL PRIMARY KEY,
    source_id    BIGINT NOT NULL REFERENCES channel_content_source (id) ON DELETE CASCADE,
    external_id  TEXT   NOT NULL UNIQUE,            -- guid / ссылка / PMID / t3_xxx — для дедупликации
    url          TEXT,
    title        TEXT,
    summary      TEXT,                              -- анонс из ленты (для AI-фильтра)
    content      TEXT,                              -- полный текст (если пришёл в ленте или скачан)
    published_at TIMESTAMP WITH TIME ZONE,
    score        INTEGER,                           -- рейтинг на Reddit
    status       VARCHAR(20) NOT NULL,              -- NEW / APPROVED / REJECTED / PROCESSED / FAILED
    ai_score     INTEGER,                           -- 0–10 от AI-фильтра
    ai_reason    TEXT,
    post_queue_id BIGINT,
    parsed_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_source_item_status ON channel_source_item (status);
CREATE INDEX idx_source_item_source ON channel_source_item (source_id);
CREATE INDEX idx_source_item_parsed ON channel_source_item (parsed_at);

-- Пост в очереди помнит, откуда он (ссылка на источник выводится в конце поста)
ALTER TABLE channel_post_queue
    ADD COLUMN source_item_id BIGINT,
    ADD COLUMN source_url     TEXT,
    ADD COLUMN source_name    TEXT,
    ADD COLUMN ai_score       INTEGER,
    ADD COLUMN review_sent_at TIMESTAMP WITH TIME ZONE;

-- Сабреддиты переезжают в общие источники — выключенными: без ключей API Reddit с сервера не отдаёт ленты.
-- Включить: /src on ID (после CHANNELPOSTER_REDDIT_CLIENT_ID / _SECRET)
INSERT INTO channel_content_source (type, name, address, active, max_items)
SELECT 'REDDIT', 'r/' || name, name, FALSE, 15
FROM subreddit
ON CONFLICT (type, address) DO NOTHING;

-- Научные новости о питании (ленты проверены 2026-10-09)
INSERT INTO channel_content_source (type, name, address, max_items) VALUES
    ('RSS', 'ScienceDaily · Nutrition',            'https://www.sciencedaily.com/rss/health_medicine/nutrition.xml', 15),
    ('RSS', 'ScienceDaily · Diet & Weight Loss',   'https://www.sciencedaily.com/rss/health_medicine/diet_and_weight_loss.xml', 15),
    ('RSS', 'ScienceDaily · Obesity',              'https://www.sciencedaily.com/rss/health_medicine/obesity.xml', 15),
    ('RSS', 'ScienceDaily · Fitness',              'https://www.sciencedaily.com/rss/health_medicine/fitness.xml', 15),
    ('RSS', 'Medical Xpress · nutrition',          'https://medicalxpress.com/rss-feed/search/?search=nutrition', 15),
    ('RSS', 'Medical Xpress · diet',               'https://medicalxpress.com/rss-feed/search/?search=diet', 15),
    ('RSS', 'Medical Xpress · weight loss',        'https://medicalxpress.com/rss-feed/search/?search=weight+loss', 15),
    ('RSS', 'The Conversation · Nutrition',        'https://theconversation.com/topics/nutrition-910/articles.atom', 10),
    ('RSS', 'The Conversation · Diet',             'https://theconversation.com/topics/diet-261/articles.atom', 10),
    ('RSS', 'The Conversation · Weight loss',      'https://theconversation.com/topics/weight-loss-684/articles.atom', 10),
    ('RSS', 'Harvard · The Nutrition Source',      'https://nutritionsource.hsph.harvard.edu/feed/', 10),
    ('RSS', 'Stronger By Science',                 'https://www.strongerbyscience.com/feed/', 10),
    ('RSS', 'Precision Nutrition',                 'https://www.precisionnutrition.com/feed', 10),
    ('RSS', 'Indicator.ru',                        'https://indicator.ru/exports/rss', 20)
ON CONFLICT (type, address) DO NOTHING;

-- Свежие исследования в PubMed (РКИ, метаанализы, обзоры)
INSERT INTO channel_content_source (type, name, address, max_items) VALUES
    ('PUBMED', 'PubMed · похудение и аппетит',
     '(weight loss[tiab] OR obesity[tiab] OR energy intake[tiab] OR satiety[tiab]) AND (randomized controlled trial[pt] OR meta-analysis[pt])', 10),
    ('PUBMED', 'PubMed · белок и мышцы',
     '(protein intake[tiab] OR muscle hypertrophy[tiab] OR resistance training[tiab]) AND (diet*[tiab] OR nutrition[tiab] OR supplement*[tiab]) AND (randomized controlled trial[pt] OR meta-analysis[pt])', 10),
    ('PUBMED', 'PubMed · продукты и привычки',
     '(ultra-processed[tiab] OR intermittent fasting[tiab] OR sugar[tiab] OR caffeine[tiab] OR breakfast[tiab] OR sweetener*[tiab]) AND (meta-analysis[pt] OR systematic review[pt])', 10)
ON CONFLICT (type, address) DO NOTHING;
