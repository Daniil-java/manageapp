--liquibase formatted sql

--changeset DanielK:77

-- Предупреждение «Premium скоро закончится» (et-121, PremiumEndingSchedulerProcessor): про какой конец Premium
-- уже предупредили. Продлил — конец сдвинулся, и про новый предупредим снова. null — ещё не предупреждали.
-- Пишется только запросом UserSettingsRepository.claimPremiumEndingNotice
ALTER TABLE user_settings ADD COLUMN premium_ending_notified_end_at TIMESTAMPTZ;
