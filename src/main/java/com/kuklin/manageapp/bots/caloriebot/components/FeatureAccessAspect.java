package com.kuklin.manageapp.bots.caloriebot.components;

import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.components.services.CalorieAccessService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserFeatureUsageService;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Аспект для автоматического контроля доступа к функциям бота.
 * Перехватывает вызовы методов, помеченных аннотацией {@link RequiresFeature}.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class FeatureAccessAspect {

    private final CalorieAccessService accessService;
    private final UserFeatureUsageService userFeatureUsageService;

    /**
     * Основная логика перехвата.
     * Проверяет наличие лимитов перед выполнением и списывает их после успешного результата.
     * Ожидается, что первым или одним из аргументов метода является Long userId (Telegram ID)
     */
    @Around("@annotation(requiresFeature)")
    public Object checkAccess(ProceedingJoinPoint joinPoint, RequiresFeature requiresFeature) throws Throwable {
        BotFeature feature = requiresFeature.value();
        BotIdentifier botIdentifier = requiresFeature.botIdentifier();

        // 1. Поиск идентификатора пользователя
        // Ожидается, что первым или одним из аргументов метода является Long userId (Telegram ID)
        Long userId = Arrays.stream(joinPoint.getArgs())
                .filter(arg -> arg instanceof Long)
                .map(arg -> (Long) arg)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Method marked @RequiresFeature must have a Long userId argument!"));

        // 2. Предварительная проверка доступа
        // Вызывает CalorieAccessService для проверки тарифного плана и остатка лимитов
        if (!accessService.hasAccess(userId, botIdentifier, feature)) {
            return AccessResult.denied(feature);
        }

        Object result;
        try {
            // Выполнение целевого метода (например, генерация отчета или распознавание фото)
            result = joinPoint.proceed();

            // 3. УМНОЕ СПИСАНИЕ ЛИМИТА (Пост-проверка)
            // Лимит списывается только если:
            // - Метод вернул AccessResult
            // - Доступ был разрешен (isAllowed)
            // - Результат не пустой (!dataIsNull) — это защищает от списания при ошибках AI или пустых данных
            if (result instanceof AccessResult<?> ar) {
                if (ar.isAllowed() && !ar.dataIsNull()) {
                    userFeatureUsageService.incrementUsage(userId, botIdentifier, feature);
                    log.info("Quota incremented for user {} on feature {}", userId, feature);
                } else {
                    log.info("Quota NOT incremented for user {} - empty data or denied", userId);
                }
            }

            return result;
        } catch (Throwable e) {
            // Если внутри метода произошла техническая ошибка (Exception),
            // лимит не списывается, так как пользователь не получил услугу.
            log.warn("Method execution failed, quota preserved for user {}", userId);
            throw e;
        }
    }
}