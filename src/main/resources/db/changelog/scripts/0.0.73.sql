--liquibase formatted sql

--changeset DanielK:76

-- Напоминания новичку без блюд (et-120, FirstDishNudgeSchedulerProcessor): сколько уже отправлено (0–2).
-- Пишется только запросом UserSettingsRepository.claimFirstDishNudge
ALTER TABLE user_settings ADD COLUMN first_dish_nudge_count SMALLINT NOT NULL DEFAULT 0;

-- Сообщения после первого блюда (et-120, FirstDishFollowUpSender): канал, приглашения, профиль — когда отправлены.
-- null — ещё нет. Пишется только запросом UserSettingsRepository.claimFirstDishFollowUp.
-- У тех, кто уже записывал еду, отмечаем сразу: им это показывали в старом /start, и следующее блюдо не первое
ALTER TABLE user_settings ADD COLUMN first_dish_follow_up_at TIMESTAMPTZ;

UPDATE user_settings u
SET first_dish_follow_up_at = NOW()
WHERE EXISTS (SELECT 1 FROM dishes d WHERE d.user_id = u.user_id);
