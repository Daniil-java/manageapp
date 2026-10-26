package com.kuklin.manageapp.aiconversation.models;

import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;

/**
 * У провайдера ИИ кончились деньги на счёте (OpenAI: 429 insufficient_quota) — все вызовы будут падать,
 * пока счёт не пополнят. Ловит AiQuotaAlertService и пишет админам со статистикой расходов.
 * details — что вернул провайдер (код и начало ответа).
 */
public record AiQuotaExhaustedEvent(ProviderVariant provider, String details) {
}
