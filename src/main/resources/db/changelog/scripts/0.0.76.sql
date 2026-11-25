--liquibase formatted sql

--changeset DanielK:79

-- Кнопка «Посмотреть пример» в приветствии и напоминаниях новичку (SampleDishCalorieUpdateHandler): когда нажата
-- впервые. null — ещё не смотрел. Нужна, чтобы видеть, помогает ли пример дойти до первого блюда, и не предлагать
-- его в напоминаниях тем, кто уже смотрел. Пишется только запросом UserSettingsRepository.claimFirstDishSample
ALTER TABLE user_settings ADD COLUMN first_dish_sample_at TIMESTAMPTZ;
