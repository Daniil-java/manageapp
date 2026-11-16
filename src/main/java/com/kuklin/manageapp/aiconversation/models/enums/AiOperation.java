package com.kuklin.manageapp.aiconversation.models.enums;

/**
 * Зачем вызывали ИИ — колонка operation в metrics_ai_interaction_record.
 * По ней видно, на что уходят деньги: фото, текст, отчёты.
 */
public enum AiOperation {
    DISH_PHOTO,
    DISH_TEXT,
    // Расшифровка голоса и разбор расшифровки
    DISH_VOICE,
    DAY_REPORT,
    PDF_REPORT,
    INSIGHT,
    // «Угадай калорийность» в канале — без пользователя
    CHANNEL_PHOTO
}
