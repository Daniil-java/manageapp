package com.kuklin.manageapp.bots.caloriebot.models.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.kuklin.manageapp.bots.caloriebot.models.AiInsightType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * ИИ-инсайт для фронта.
 * payload — ответ ИИ как JSON-объект (структура зависит от type).
 * stale — инсайт устарел, стоит предложить обновить.
 * language — язык текста ("en", "ru"); null — старый инсайт, английский.
 */
public record AiInsightDto(AiInsightType type,
                           LocalDate periodFrom,
                           LocalDate periodTo,
                           Instant createdAt,
                           boolean stale,
                           JsonNode payload,
                           String language) {
}
