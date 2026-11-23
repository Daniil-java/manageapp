package com.kuklin.manageapp.bots.caloriebot.access;

import com.kuklin.manageapp.bots.caloriebot.access.model.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.access.model.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.access.model.EmptyResultCharge;
import com.kuklin.manageapp.bots.caloriebot.access.model.QuotaCharge;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Аспект для автоматического контроля доступа к функциям бота.
 * Перехватывает вызовы методов, помеченных аннотацией {@link RequiresFeature}:
 * DishService.getDishDtoByPhoto (фото), ReportService (отчёты за день / неделю / месяц),
 * UserFavoriteDishService.saveFromDish (избранное).
 * <p>
 * Схема: списать попытку → вызвать метод → вернуть попытку, если услуга не оказана.
 * Раньше было наоборот (проверить → вызвать → списать), и между проверкой и списанием
 * проходили секунды вызова ИИ — параллельные запросы успевали пройти сверх лимита.
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
     * userId (appUserId) берётся из параметра с именем {@link RequiresFeature#userIdParam()}
     *
     * @return результат метода; AccessResult.denied — если лимит исчерпан (метод не вызывался);
     *         для фото без еды — AccessResult с emptyResultCharge FORGIVEN / CHARGED, чтобы вызывающий код
     *         сказал пользователю, засчитана ли попытка
     */
    @Around("@annotation(requiresFeature)")
    public Object checkAccess(ProceedingJoinPoint joinPoint, RequiresFeature requiresFeature) throws Throwable {
        BotFeature feature = requiresFeature.value();
        BotIdentifier botIdentifier = requiresFeature.botIdentifier();

        // 1. Идентификатор пользователя — параметр с именем из аннотации (по умолчанию userId)
        Long userId = findUserId(joinPoint, requiresFeature.userIdParam());

        // 2. Проверка доступа и списание попытки — до вызова, одной атомарной командой в БД
        // (лимит проверяется условием used_count < limit в самом UPDATE).
        QuotaCharge charge = accessService.tryConsume(userId, botIdentifier, feature);

        // Фичи нет в тарифе или лимит исчерпан — метод не вызываем, вызывающий код покажет «купите подписку»
        if (charge == QuotaCharge.DENIED) {
            return AccessResult.denied(feature);
        }
        // Безлимит — ничего не списано, значит и возвращать нечего: просто вызываем метод
        if (charge == QuotaCharge.UNLIMITED) {
            return joinPoint.proceed();
        }

        // Дальше — только CHARGED: попытка уже списана
        Object result;
        try {
            // Выполнение целевого метода (например, генерация отчета или распознавание фото)
            result = joinPoint.proceed();
        } catch (Throwable e) {
            // Техническая ошибка (OpenAI недоступен, таймаут, ошибка БД) — пользователь не получил услугу,
            // попытку возвращаем и пробрасываем исключение дальше как есть
            refund(userId, botIdentifier, feature);
            log.warn("Method execution failed, quota refunded for user {} on feature {}", userId, feature);
            throw e;
        }

        // Результат с данными — услуга оказана, попытка остаётся списанной
        if (!(result instanceof AccessResult<?> ar) || !ar.dataIsNull()) {
            return result;
        }

        // 3. Пустой результат (data == null). Что он значит — зависит от фичи
        if (!requiresFeature.forgiveEmptyOncePerDay()) {
            // Отчёты: внутри они ловят ошибки ИИ / рендера PDF и возвращают null —
            // это сбой, а не ответ ИИ. Попытку возвращаем, как и раньше (раньше просто не списывали)
            refund(userId, botIdentifier, feature);
            log.info("Empty result, quota refunded for user {} on feature {}", userId, feature);
            return result;
        }

        // Фото: ИИ ответил, но еды не нашёл (мусорное фото) — токены уже потрачены.
        // Первый раз за день прощаем: возвращаем попытку, пользователь видит «в этот раз не засчитали».
        if (userFeatureUsageService.tryUseDailyGrace(userId, botIdentifier, feature)) {
            refund(userId, botIdentifier, feature);
            log.info("Empty result forgiven (once a day) for user {} on feature {}", userId, feature);
            return ar.withEmptyResultCharge(EmptyResultCharge.FORGIVEN);
        }
        // Сегодня уже прощали — попытка остаётся списанной, пользователь видит «попытка засчитана».
        // Иначе мусорными фото можно было бы обходить квоту бесконечно.
        log.info("Empty result charged for user {} on feature {}", userId, feature);
        return ar.withEmptyResultCharge(EmptyResultCharge.CHARGED);
    }

    /**
     * Значение параметра с именем paramName. Имена параметров есть в рантайме благодаря флагу -parameters (pom.xml).
     * Нет такого параметра, он не Long или null — ошибка разработчика, падаем до списания квоты.
     */
    static Long findUserId(ProceedingJoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < names.length; i++) {
            if (paramName.equals(names[i]) && args[i] instanceof Long userId) {
                return userId;
            }
        }
        throw new IllegalStateException("Method " + signature.toShortString()
                + " marked @RequiresFeature must have a non-null Long parameter '" + paramName + "'");
    }

    /**
     * Возвращает попытку. Ошибку возврата только логируем: если метод упал, наружу должна уйти
     * его исходная ошибка, а не ошибка возврата.
     */
    private void refund(Long userId, BotIdentifier botIdentifier, BotFeature feature) {
        try {
            userFeatureUsageService.refundUsage(userId, botIdentifier, feature);
        } catch (Exception e) {
            log.error("Failed to refund quota for user {} on feature {}", userId, feature, e);
        }
    }
}
