package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.UserFeatureUsage;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UserFeatureUsageRepository;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitPeriod;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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
     * Проверяет лимит и списывает одну попытку — одной командой в БД.
     * <p>
     * Кто вызывает: {@link CalorieAccessService#tryConsume}, а его — {@link com.kuklin.manageapp.bots.caloriebot.components.FeatureAccessAspect}
     * перед каждым методом с @RequiresFeature (фото, отчёты, избранное), только если у фичи есть лимит.
     * <p>
     * Зачем так: раньше лимит проверялся чтением счётчика, а списывался после вызова ИИ (через 10–30 с).
     * Пачка параллельных запросов успевала прочитать старое значение и проходила сверх лимита.
     * Теперь проверка — условие в UPDATE (used_count &lt; limit): база выполняет такие UPDATE по очереди,
     * и сверх лимита не проходит ни один.
     * <p>
     * REQUIRES_NEW — своя транзакция, коммитится сразу:
     * <ul>
     * <li>другие запросы этого пользователя сразу видят списание;</li>
     * <li>строка не остаётся заблокированной, пока вызывающий метод ждёт ответ ИИ;</li>
     * <li>если вызывающий метод потом упадёт и его транзакция откатится, списание не откатится вместе с ней —
     * возвращать попытку будет {@link #refundUsage}, явно.</li>
     * </ul>
     *
     * @param limit лимит из тарифа (PlanFeature.limitValue)
     * @return true — попытка списана, false — лимит исчерпан
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryConsume(Long userId, BotIdentifier botIdentifier, BotFeature feature,
                              FeatureLimitPeriod limitPeriod, int limit) {
        // 1. Строка счётчика должна существовать (UPDATE не создаёт строк), а в новом периоде (день / месяц) —
        // быть обнулённой. Это делает тот же метод, что и раньше: с учётом таймзоны и защиты от её смены.
        getUserFeatureUsageByUserIdAndBotIdentifierAndBotFeatureOrCreate(userId, botIdentifier, feature, limitPeriod);

        // 2. Списание с проверкой лимита: 1 строка обновлена — списали, 0 — лимит исчерпан
        return userFeatureUsageRepository.tryIncrementUsage(userId, botIdentifier, feature, limit) > 0;
    }

    /**
     * Возвращает попытку, списанную {@link #tryConsume}, — пользователь не получил услугу.
     * <p>
     * Кто вызывает: только {@link com.kuklin.manageapp.bots.caloriebot.components.FeatureAccessAspect}, когда:
     * <ul>
     * <li>метод с @RequiresFeature бросил исключение (например, OpenAI недоступен);</li>
     * <li>метод вернул пустой результат, а фича не прощает пустые ответы (отчёты: внутри они ловят ошибки
     * и возвращают null);</li>
     * <li>фото без еды, но это первая такая промашка за день ({@link #tryUseDailyGrace} вернул true).</li>
     * </ul>
     * REQUIRES_NEW — по той же причине, что и в {@link #tryConsume}: возврат не должен откатиться вместе
     * с упавшей транзакцией вызывающего метода. Счётчик не уходит ниже нуля (условие в UPDATE).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundUsage(Long userId, BotIdentifier botIdentifier, BotFeature feature) {
        userFeatureUsageRepository.decrementUsage(userId, botIdentifier, feature);
    }

    /**
     * «Бесплатная промашка»: можно ли простить пустой ответ ИИ — не чаще раза в день.
     * <p>
     * Кто вызывает: только {@link com.kuklin.manageapp.bots.caloriebot.components.FeatureAccessAspect} —
     * для фич с @RequiresFeature(forgiveEmptyOncePerDay = true) (сейчас это фото), когда ИИ ответил,
     * но ничего не нашёл. true — аспект возвращает попытку и пользователь видит «в этот раз не засчитали»;
     * false — попытка остаётся списанной, «попытка засчитана».
     * <p>
     * Зачем: раньше пустой ответ ИИ квоту не тратил вообще, и мусорными фото её можно было обходить бесконечно,
     * хотя токены ИИ сгорали. А честного пользователя, у которого ИИ не узнал еду, наказывать с первого раза не хотим.
     * <p>
     * Как: UPDATE ставит в last_grace_local_date сегодняшнюю дату, только если там не сегодня.
     * Из нескольких параллельных мусорных фото дату поставит (и получит true) только один запрос.
     * «Сегодня» — по таймзоне пользователя, как и сброс дневного лимита.
     *
     * @return true — сегодня ещё не прощали, отметка поставлена; false — сегодня уже прощали
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryUseDailyGrace(Long userId, BotIdentifier botIdentifier, BotFeature feature) {
        LocalDate today = LocalDate.now(userSettingsService.getOrCreate(userId).getZoneId());
        return userFeatureUsageRepository.markGraceUsed(userId, botIdentifier, feature, today) > 0;
    }

    /**
     * Уменьшает счетчик (полезно при отмене операции или возврате средств).
     * Кто вызывает: UserFavoriteDishService.deleteFavorite — удалили блюдо из избранного, слот освободился.
     * В отличие от {@link #refundUsage} — в транзакции вызывающего: если удаление откатится, откатится и возврат.
     */
    @Transactional
    public void decrementUsage(Long userId, BotIdentifier botIdentifier, BotFeature feature) {
        userFeatureUsageRepository.decrementUsage(userId, botIdentifier, feature);
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