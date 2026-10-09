package com.kuklin.manageapp.bots.caloriebot.models;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы ИИ-инсайтов на странице Insights.
 * periodDays — за сколько последних дней (включая сегодня) строится инсайт.
 * staleAfterDays — через сколько дней после генерации инсайт считается устаревшим.
 */
@Getter
@RequiredArgsConstructor
public enum AiInsightType {
    WEEKLY_SUMMARY(7, 1),
    MONTHLY_SUMMARY(30, 7),
    PATTERNS_WEEK(7, 1),
    PATTERNS_MONTH(30, 7);

    private final int periodDays;
    private final int staleAfterDays;
}
