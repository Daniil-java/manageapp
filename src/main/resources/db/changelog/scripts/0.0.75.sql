--liquibase formatted sql

--changeset DanielK:78

-- Отложенное приветствие /start (StartUpdateHandler): пока новичок не выбрал язык, здесь лежит то, что покажем
-- после выбора. Раньше — в памяти, и деплой между /start и выбором языка терял калькулятор с лендинга.
-- pending_start_calculator — данные калькулятора (CalculatorStartParam.Calculator.encode, «p_F_28_168_65_M_L»),
-- pending_start_trial_days — дней пробного периода, pending_start_bonus_days — дней бонуса партнёра.
-- null — нечего показывать. Пишутся только запросами UserSettingsRepository.savePendingStart / clearPendingStart
ALTER TABLE user_settings ADD COLUMN pending_start_calculator VARCHAR(32);
ALTER TABLE user_settings ADD COLUMN pending_start_trial_days SMALLINT;
ALTER TABLE user_settings ADD COLUMN pending_start_bonus_days SMALLINT;
