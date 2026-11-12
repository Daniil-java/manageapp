--liquibase formatted sql
--changeset DanielK:50 splitStatements:false

-- Синхронизирует sequence каждой таблицы с колонкой id (BIGSERIAL/IDENTITY)
-- с реальным MAX(id) в этой таблице.
--
-- Зачем: если в таблицу когда-либо вставлялись строки в обход sequence
-- (restore дампа, ручной INSERT с явным id, COPY) — sequence остаётся
-- "позади" факта и рано или поздно начинает выдавать id, которые уже
-- заняты -> "duplicate key value violates unique constraint ..._pkey".
--
-- Безопасно гонять повторно: если sequence уже синхронна или впереди
-- max(id) — ничего не меняется (используется GREATEST).

DO $$
DECLARE
r RECORD;
    seq_name TEXT;
    seq_val BIGINT;
    max_val BIGINT;
BEGIN
FOR r IN
SELECT c.table_name
FROM information_schema.columns c
WHERE c.column_name = 'id'
  AND c.table_schema = 'public'
    LOOP
        seq_name := pg_get_serial_sequence('public.' || r.table_name, 'id');

IF seq_name IS NOT NULL THEN
            EXECUTE format('SELECT last_value FROM %s', seq_name) INTO seq_val;
EXECUTE format('SELECT COALESCE(MAX(id), 0) FROM %I', r.table_name) INTO max_val;

IF seq_val < max_val THEN
                RAISE NOTICE 'Синхронизирую %: seq % -> %', r.table_name, seq_val, max_val;
EXECUTE format('SELECT setval(%L, %s)', seq_name, max_val);
END IF;
END IF;
END LOOP;
END $$;

-- БЛОК ОТКАТА (ROLLBACK)
-- Откатывать здесь нечего по смыслу — setval двигает sequence вперёд
-- к уже существующим данным, откат ничего не восстановит осмысленно.
-- rollback SELECT 1;