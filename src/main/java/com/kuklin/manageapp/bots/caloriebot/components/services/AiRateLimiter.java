package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.configurations.AiRateLimitProperties;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ограничивает, как часто один пользователь может обращаться к ИИ: текст, фото, голос, отчёты, инсайты.
 * Лимиты — {@link AiRateLimitProperties} (application.yaml, calorie.ai-rate-limit):
 * не больше per-minute за последние 60 секунд и не больше per-day за последние 24 часа
 * (per-day-premium — с активной подпиской).
 *
 * Как устроено: на каждого пользователя храним время его обращений к ИИ за последние 24 часа
 * (очередь, старые в начале). При новом обращении выкидываем всё старше суток, считаем,
 * сколько осталось за минуту и за сутки, и либо разрешаем (добавляем текущее время), либо отказываем.
 * Считаются попытки, а не успешные ответы: неудачный вызов ИИ тоже стоит денег.
 *
 * Хранится в памяти: работает, пока бэк в одном экземпляре; после перезапуска счётчики обнуляются.
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

    private final AiRateLimitProperties limits;
    private final UserSubscriptionService userSubscriptionService;
    private final Clock clock;

    // appUserId → время обращений к ИИ за последние 24 часа, по возрастанию
    private final Map<Long, Deque<Instant>> callsByUser = new ConcurrentHashMap<>();

    @Autowired
    public AiRateLimiter(AiRateLimitProperties limits, UserSubscriptionService userSubscriptionService) {
        this(limits, userSubscriptionService, Clock.systemUTC());
    }

    // Для тестов — подменяем часы
    AiRateLimiter(AiRateLimitProperties limits, UserSubscriptionService userSubscriptionService, Clock clock) {
        this.limits = limits;
        this.userSubscriptionService = userSubscriptionService;
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
     */
    public Decision tryAcquire(Long appUserId) {
        Instant now = clock.instant();
        Deque<Instant> calls = callsByUser.computeIfAbsent(appUserId, id -> new ArrayDeque<>());

        // synchronized на очередь пользователя: параллельные запросы одного пользователя
        // не проскочат проверку одновременно, а разные пользователи друг друга не ждут
        synchronized (calls) {
            removeOlderThan(calls, now.minus(DAY));

            // 1. Минутный лимит
            int perMinute = limits.getPerMinute();
            if (countSince(calls, now.minus(MINUTE)) >= perMinute) {
                // Место освободится, когда из минутного окна выпадет самое раннее из последних perMinute обращений
                Instant oldestInWindow = nthFromEnd(calls, perMinute);
                return denied(Reason.MINUTE, oldestInWindow.plus(MINUTE), now, false, limits.getPerDay());
            }

            // 2. Суточный лимит. Подписку проверяем только когда дошли до бесплатного лимита —
            // чтобы не ходить в БД на каждый запрос
            boolean premium = false;
            int dailyLimit = limits.getPerDay();
            if (calls.size() >= dailyLimit) {
                premium = userSubscriptionService.hasActiveSubscription(appUserId, BotIdentifier.CALORIE_BOT);
                if (premium) {
                    dailyLimit = limits.getPerDayPremium();
                }
            }
            if (calls.size() >= dailyLimit) {
                Instant oldestInWindow = nthFromEnd(calls, dailyLimit);
                log.warn("AI daily limit reached for user {} ({} per day, premium={})", appUserId, dailyLimit, premium);
                return denied(Reason.DAY, oldestInWindow.plus(DAY), now, premium, dailyLimit);
            }

            calls.addLast(now);
            return new Decision(true, null, 0, premium, dailyLimit);
        }
    }

    /**
     * Для API: проверяет лимиты и при отказе бросает 429 (AI_RATE_LIMIT или AI_DAILY_LIMIT)
     * с указанием, через сколько можно повторить.
     */
    public void acquireOrThrow(Long appUserId) {
        Decision decision = tryAcquire(appUserId);
        if (decision.allowed()) return;

        if (decision.reason() == Reason.MINUTE) {
            throw new ErrorResponseException(ErrorStatus.AI_RATE_LIMIT,
                    String.format("Too many AI requests. Try again in %d seconds.", decision.retryAfterSeconds()));
        }
        throw new ErrorResponseException(ErrorStatus.AI_DAILY_LIMIT,
                String.format("Daily AI limit reached (%d per 24 hours). Try again in %s.",
                        decision.dailyLimit(), formatWait(decision.retryAfterSeconds())));
    }

    /**
     * Раз в час убираем пользователей, которые не обращались к ИИ больше суток, — чтобы память не росла.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void evictIdleUsers() {
        Instant dayAgo = clock.instant().minus(DAY);
        callsByUser.entrySet().removeIf(entry -> {
            Deque<Instant> calls = entry.getValue();
            synchronized (calls) {
                removeOlderThan(calls, dayAgo);
                return calls.isEmpty();
            }
        });
    }

    // --- Private ---

    private static Decision denied(Reason reason, Instant freesAt, Instant now, boolean premium, int dailyLimit) {
        // Округляем вверх и минимум 1 секунда — чтобы не ответить «повторите через 0 секунд»
        long seconds = Math.max(1, (Duration.between(now, freesAt).toMillis() + 999) / 1000);
        return new Decision(false, reason, seconds, premium, dailyLimit);
    }

    /** Выкидывает из начала очереди обращения не позже границы (ровно 24 часа назад — уже не считается). */
    private static void removeOlderThan(Deque<Instant> calls, Instant border) {
        while (!calls.isEmpty() && !calls.peekFirst().isAfter(border)) {
            calls.pollFirst();
        }
    }

    /** Сколько обращений было позже момента since (идём с конца — там самые свежие). */
    private static int countSince(Deque<Instant> calls, Instant since) {
        int count = 0;
        Iterator<Instant> it = calls.descendingIterator();
        while (it.hasNext() && it.next().isAfter(since)) {
            count++;
        }
        return count;
    }

    /** n-е обращение с конца (n = 1 — самое последнее). */
    private static Instant nthFromEnd(Deque<Instant> calls, int n) {
        Iterator<Instant> it = calls.descendingIterator();
        Instant result = null;
        for (int i = 0; i < n && it.hasNext(); i++) {
            result = it.next();
        }
        return result;
    }

    /** 125 → "3 min", 7300 → "2 h 2 min". */
    private static String formatWait(long seconds) {
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) return minutes + " min";
        return minutes / 60 + " h " + minutes % 60 + " min";
    }
}
