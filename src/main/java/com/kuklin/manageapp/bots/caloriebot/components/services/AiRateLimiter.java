package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.CalorieAiRequestRepository;
import com.kuklin.manageapp.bots.caloriebot.configurations.AiRateLimitProperties;
import com.kuklin.manageapp.bots.caloriebot.entities.CalorieAiRequest;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorParams;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Ограничивает, как часто один пользователь может обращаться к ИИ: текст, фото, голос, отчёты, инсайты.
 * Лимиты — {@link AiRateLimitProperties} (application.yaml, calorie.ai-rate-limit):
 * не больше per-minute за последние 60 секунд и не больше per-day за последние 24 часа
 * (per-day-premium — с активной подпиской).
 *
 * Как устроено: каждое разрешённое обращение — строка в calorie_ai_request. При новом обращении считаем строки
 * пользователя за минуту и за сутки и либо разрешаем (добавляем строку), либо отказываем.
 * Считаются попытки, а не успешные ответы: неудачный вызов ИИ тоже стоит денег.
 * Хранится в БД: счётчики переживают перезапуск и общие для всех экземпляров бэка.
 *
 * Где вызывается — в точках входа, один раз на действие пользователя:
 * API — DishService (text / photo / voice), CalorieAiInsightService.refresh;
 * бот — DishUpdateHandler, CalorieReportUpdateHandler.
 * Ежедневная сводка от планировщика не считается — её запускает система, а не пользователь.
 */
@Slf4j
@Component
public class AiRateLimiter {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration DAY = Duration.ofDays(1);
    // Сколько хранить обращения: для лимитов хватает суток, остальное — чтобы видеть расход ИИ по пользователям
    private static final Duration KEEP_REQUESTS = Duration.ofDays(30);

    private final AiRateLimitProperties limits;
    private final UserSubscriptionService userSubscriptionService;
    private final CalorieAiRequestRepository calorieAiRequestRepository;
    private final Clock clock;

    @Autowired
    public AiRateLimiter(AiRateLimitProperties limits, UserSubscriptionService userSubscriptionService,
                         CalorieAiRequestRepository calorieAiRequestRepository) {
        this(limits, userSubscriptionService, calorieAiRequestRepository, Clock.systemUTC());
    }

    // Для тестов — подменяем часы
    AiRateLimiter(AiRateLimitProperties limits, UserSubscriptionService userSubscriptionService,
                  CalorieAiRequestRepository calorieAiRequestRepository, Clock clock) {
        this.limits = limits;
        this.userSubscriptionService = userSubscriptionService;
        this.calorieAiRequestRepository = calorieAiRequestRepository;
        this.clock = clock;
    }

    /**
     * Результат проверки.
     *
     * @param allowed           можно обращаться к ИИ (обращение уже засчитано)
     * @param reason            почему отказали: MINUTE или DAY; null, если разрешено
     * @param retryAfterSeconds через сколько секунд освободится место; 0, если разрешено
     * @param premium           есть ли у пользователя подписка (проверяется только у суточного лимита, иначе false)
     * @param dailyLimit        суточный лимит, который применили к пользователю
     */
    public record Decision(boolean allowed, Reason reason, long retryAfterSeconds, boolean premium, int dailyLimit) {
    }

    public enum Reason {MINUTE, DAY}

    /**
     * Проверяет лимиты и, если можно, засчитывает обращение.
     * Для бота: при отказе обработчик сам отправляет сообщение (FeatureLimitNotifier.sendAiRateLimited).
     * <p>
     * Своя транзакция (REQUIRES_NEW): обращение засчитывается сразу, даже если вызывающий код потом упадёт
     * и откатит свою, а блокировка пользователя держится миллисекунды, а не всё время ответа ИИ.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Decision tryAcquire(Long appUserId) {
        // Параллельные запросы одного пользователя не проскочат проверку одновременно —
        // ждут здесь, пока текущий не допишет обращение и не закроет транзакцию
        calorieAiRequestRepository.lockUserRequests(appUserId);
        Instant now = clock.instant();

        // 1. Минутный лимит
        int perMinute = limits.getPerMinute();
        if (calorieAiRequestRepository.countByAppUserIdAndCreatedAtAfter(appUserId, now.minus(MINUTE)) >= perMinute) {
            // Место освободится, когда из минутного окна выпадет самое раннее из последних perMinute обращений
            Instant oldestInWindow = findNthLatestRequestTime(appUserId, now.minus(MINUTE), perMinute);
            return denied(Reason.MINUTE, oldestInWindow.plus(MINUTE), now, false, limits.getPerDay());
        }

        // 2. Суточный лимит. Подписку проверяем только когда дошли до бесплатного лимита —
        // чтобы не ходить за ней на каждый запрос
        long callsToday = calorieAiRequestRepository.countByAppUserIdAndCreatedAtAfter(appUserId, now.minus(DAY));
        boolean premium = false;
        int dailyLimit = limits.getPerDay();
        if (callsToday >= dailyLimit) {
            premium = userSubscriptionService.hasActiveSubscription(appUserId, BotIdentifier.CALORIE_BOT);
            if (premium) {
                dailyLimit = limits.getPerDayPremium();
            }
        }
        if (callsToday >= dailyLimit) {
            Instant oldestInWindow = findNthLatestRequestTime(appUserId, now.minus(DAY), dailyLimit);
            log.warn("AI daily limit reached for user {} ({} per day, premium={})", appUserId, dailyLimit, premium);
            return denied(Reason.DAY, oldestInWindow.plus(DAY), now, premium, dailyLimit);
        }

        calorieAiRequestRepository.save(new CalorieAiRequest().setAppUserId(appUserId).setCreatedAt(now));
        return new Decision(true, null, 0, premium, dailyLimit);
    }

    /**
     * Для API: проверяет лимиты и при отказе бросает 429 (AI_RATE_LIMIT или AI_DAILY_LIMIT)
     * с указанием, через сколько можно повторить.
     * Транзакция — как у tryAcquire (вызов изнутри класса идёт мимо прокси, поэтому аннотация и здесь).
     * Исключение её откатывает, но при отказе в неё ничего не записано.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acquireOrThrow(Long appUserId) {
        Decision decision = tryAcquire(appUserId);
        if (decision.allowed()) return;

        if (decision.reason() == Reason.MINUTE) {
            throw new ErrorResponseException(ErrorStatus.AI_RATE_LIMIT,
                    ErrorParams.retryAfter(decision.retryAfterSeconds()));
        }
        throw new ErrorResponseException(ErrorStatus.AI_DAILY_LIMIT,
                ErrorParams.dailyLimit(decision.retryAfterSeconds(), decision.dailyLimit()));
    }

    /**
     * Раз в сутки удаляем обращения старше {@link #KEEP_REQUESTS} — чтобы таблица не росла.
     */
    @Scheduled(fixedRate = 24 * 60 * 60 * 1000)
    @Transactional
    public void deleteOldRequests() {
        int deleted = calorieAiRequestRepository.deleteCreatedBefore(clock.instant().minus(KEEP_REQUESTS));
        log.info("Deleted {} AI requests older than {} days", deleted, KEEP_REQUESTS.toDays());
    }

    // --- Private ---

    /** Время n-го с конца обращения позже since (n = 1 — самое последнее). */
    private Instant findNthLatestRequestTime(Long appUserId, Instant since, int n) {
        return calorieAiRequestRepository
                .findByAppUserIdAndCreatedAtAfterOrderByCreatedAtDesc(appUserId, since, PageRequest.of(n - 1, 1))
                .get(0)
                .getCreatedAt();
    }

    private static Decision denied(Reason reason, Instant freesAt, Instant now, boolean premium, int dailyLimit) {
        // Округляем вверх и минимум 1 секунда — чтобы не ответить «повторите через 0 секунд»
        long seconds = Math.max(1, (Duration.between(now, freesAt).toMillis() + 999) / 1000);
        return new Decision(false, reason, seconds, premium, dailyLimit);
    }
}
