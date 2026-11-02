package com.kuklin.manageapp.bots.caloriebot.models.events;

/**
 * Пользователь добавил блюдо в дневник (ИИ, вручную, из избранного; бот и миниаппка).
 * Публикует DishService, слушатели получают его после коммита — блюдо уже в БД.
 */
public record DishAddedEvent(Long appUserId) {
}
