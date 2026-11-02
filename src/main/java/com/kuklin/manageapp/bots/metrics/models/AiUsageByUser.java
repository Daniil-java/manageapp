package com.kuklin.manageapp.bots.metrics.models;

import java.math.BigDecimal;

/**
 * Расходы ИИ одного пользователя за период — по metrics_ai_interaction_record.
 * appUserId null — вызовы без пользователя (другие боты, фоновые задачи).
 * costUsd null — ни у одного вызова нет цены (модель не из AiModelPrice).
 * Типы-обёртки — под конструктор в JPQL (count и sum отдают Long).
 */
public record AiUsageByUser(Long appUserId, Long requests, Long tokens, BigDecimal costUsd) {

    public long getRequestsOrZero() {
        return requests != null ? requests : 0L;
    }

    public long getTokensOrZero() {
        return tokens != null ? tokens : 0L;
    }

    public BigDecimal getCostOrZero() {
        return costUsd != null ? costUsd : BigDecimal.ZERO;
    }
}
