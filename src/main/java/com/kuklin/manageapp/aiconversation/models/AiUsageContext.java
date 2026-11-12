package com.kuklin.manageapp.aiconversation.models;

import com.kuklin.manageapp.aiconversation.models.enums.AiOperation;

/**
 * Чей это вызов ИИ и зачем — для учёта расходов (metrics_ai_interaction_record.app_user_id / operation).
 * Код бота открывает контекст вокруг обращения к ИИ, провайдер читает его, когда пишет usage:
 * <pre>
 * try (var ignored = AiUsageContext.open(userId, AiOperation.DISH_PHOTO)) { ... }
 * </pre>
 * ThreadLocal: запрос к ИИ (Feign) синхронный и идёт в том же потоке.
 * Внешний контекст главнее вложенного: голосовое — это расшифровка и разбор текста,
 * и оба вызова записываются как DISH_VOICE, хотя разбор текста сам открывает DISH_TEXT.
 */
public final class AiUsageContext implements AutoCloseable {
    private static final ThreadLocal<AiUsageContext> CURRENT = new ThreadLocal<>();
    // Вложенный open при уже открытом контексте: ничего не меняет и ничего не закрывает
    private static final AiUsageContext NESTED = new AiUsageContext(null, null);

    private final Long appUserId;
    private final AiOperation operation;

    private AiUsageContext(Long appUserId, AiOperation operation) {
        this.appUserId = appUserId;
        this.operation = operation;
    }

    /** appUserId — null, если вызов ни к какому пользователю не относится (пост в канал). */
    public static AiUsageContext open(Long appUserId, AiOperation operation) {
        if (CURRENT.get() != null) return NESTED;
        AiUsageContext context = new AiUsageContext(appUserId, operation);
        CURRENT.set(context);
        return context;
    }

    public static Long getCurrentAppUserIdOrNull() {
        AiUsageContext context = CURRENT.get();
        return context != null ? context.appUserId : null;
    }

    public static AiOperation getCurrentOperationOrNull() {
        AiUsageContext context = CURRENT.get();
        return context != null ? context.operation : null;
    }

    @Override
    public void close() {
        if (this != NESTED) CURRENT.remove();
    }
}
