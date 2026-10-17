package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFeatureUsage;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UserFeatureUsageRepository;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitPeriod;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Сервис для управления жизненным циклом счетчиков использования фич пользователями.
 * Отвечает за инкремент, декремент и умный сброс лимитов на стыке календарных периодов.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserFeatureUsageService {
    private final UserFeatureUsageRepository userFeatureUsageRepository;
    private final UserSettingsService userSettingsService;
    private final PlanFeatureService planFeatureService;
    private final CommonPaymentFacade commonPaymentFacade;

    /**
     * Возвращает объект использования фичи, предварительно проверяя необходимость сброса счетчика.
     * Если записи нет — создает новую с учетом таймзоны пользователя.
     */
    @Transactional
    public UserFeatureUsage getUserFeatureUsageByUserIdAndBotIdentifierAndBotFeatureOrCreate(
            Long userId, BotIdentifier botIdentifier,
            BotFeature botFeature, FeatureLimitPeriod featureLimitPeriod
    ) {
        Optional<UserFeatureUsage> userFeatureUsageOpt = userFeatureUsageRepository
                .findByUserIdAndFeatureAndAndBotIdentifier(
                        userId, botFeature, botIdentifier
                );

        // Получаем часовой пояс пользователя для корректного определения наступления "нового дня"
        ZoneId zoneId = userSettingsService.getOrCreate(userId).getZoneId();

        if (userFeatureUsageOpt.isPresent()) {
            UserFeatureUsage usage = userFeatureUsageOpt.get();
            // Проверяем, наступил ли срок обнуления лимита (новые сутки/месяц)
            if (shouldReset(usage, featureLimitPeriod, zoneId)) {
                return resetUsage(usage, zoneId);
            } else {
                return usage;
            }
        }

        // Создание новой записи для пользователя, который впервые использует фичу
        return userFeatureUsageRepository.save(
                new UserFeatureUsage()
                        .setUserId(userId)
                        .setBotIdentifier(botIdentifier)
                        .setFeature(botFeature)
                        .setUsedCount(0)
                        .setLastResetLocalDate(LocalDate.now(zoneId))
                        .setLastResetUtc(LocalDateTime.now(ZoneOffset.UTC))
        );
    }


    /**
     * Увеличивает счетчик использования функции на 1.
     * Теперь безопасно создает запись, если её еще нет.
     */
    @Transactional
    public void incrementUsage(Long userId, BotIdentifier botIdentifier, BotFeature feature) {
        PlanFeature planFeature = planFeatureService
                .getFeatureByUserIdAndBotIdentifierAndFeatureOrNull(
                        userId, botIdentifier, feature);

        if (planFeature == null) {
            log.error("PlanFeature dont exist in pricing plan! userId {}, botFeature {}, bot {}", userId, feature.name(), botIdentifier);
            return;
        }

        userFeatureUsageRepository.incrementUsage(userId, botIdentifier, feature);
    }

    /**
     * Уменьшает счетчик (полезно при отмене операции или возврате средств).
     */
    @Transactional
    public void decrementUsage(Long userId, BotIdentifier botIdentifier, BotFeature feature) {
        userFeatureUsageRepository.findByUserIdAndFeatureAndAndBotIdentifier(userId, feature, botIdentifier)
                .ifPresent(usage -> {
                    if (usage.getUsedCount() > 0) {
                        usage.setUsedCount(usage.getUsedCount() - 1);
                        userFeatureUsageRepository.save(usage);
                    }
                });
    }

    /**
     * Обнуляет счетчик и обновляет метки времени сброса.
     */
    public UserFeatureUsage resetUsage(UserFeatureUsage userFeatureUsage, ZoneId userZoneId) {
        return userFeatureUsageRepository.save(
                userFeatureUsage
                        .setLastResetLocalDate(LocalDate.now(userZoneId))
                        .setLastResetUtc(LocalDateTime.now(ZoneOffset.UTC))
                        .setUsedCount(0)
        );
    }

    /**
     * Ключевая логика проверки необходимости сброса лимита.
     * Реализует защиту от злоупотреблений сменой часовых поясов.
     */
    private boolean shouldReset(UserFeatureUsage usage, FeatureLimitPeriod period, ZoneId userZone) {
        if (usage.getLastResetUtc() == null) return true;

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate userToday = LocalDate.now(userZone);

        // Минимальный интервал между сбросами (защита от частой смены ZoneId в настройках)
        int minResetTime = 18;

        return switch (period) {
            case DAILY -> {
                // 1. Проверка по календарю пользователя (наступило ли завтра?)
                boolean isNewLocalDay = userToday.isAfter(usage.getLastResetLocalDate());

                // 2. Проверка по "физическому" времени (прошло ли достаточно часов?)
                // Если юзер сменил зону с UTC+12 на UTC-12, по календарю наступит "завтра",
                // но в реальности пройдет всего пара часов. Порог в 18 часов отсекает такие манипуляции.
                long hoursPassed = ChronoUnit.HOURS.between(usage.getLastResetUtc(), nowUtc);
                boolean isNotCheat = hoursPassed >= minResetTime;

                yield isNewLocalDay && isNotCheat;
            }

            case MONTHLY -> {
                // Сравнение месяцев по календарю пользователя
                YearMonth lastMonth = YearMonth.from(usage.getLastResetLocalDate());
                YearMonth currentMonth = YearMonth.from(userToday);
                yield currentMonth.isAfter(lastMonth);
            }

            default -> false;
        };
    }
}