package com.kuklin.manageapp.aiconversation.providers.impl;

import com.kuklin.manageapp.aiconversation.models.AiUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Предохранитель для вызовов OpenAI: когда OpenAI лежит, не держать потоки пула апдейтов
 * по таймауту чтения на каждый запрос, а сразу отвечать «ИИ недоступен».
 *
 * - Закрыт: запросы идут, считаем подряд идущие сбои (таймаут/сеть или 5xx).
 * - После FAILURE_THRESHOLD сбоев подряд — открыт на OPEN_DURATION: вызовы падают сразу.
 * - Время вышло — пропускаем один пробный запрос, остальные пока падают сразу.
 *   Пробный прошёл — закрываем, упал — снова открываем.
 * Ответ 4xx (ключ, лимит частоты, плохой запрос) — OpenAI жив, это не сбой для предохранителя.
 */
@Slf4j
@Component
public class OpenAiCircuitBreaker {
    static final int FAILURE_THRESHOLD = 5;
    static final Duration OPEN_DURATION = Duration.ofSeconds(30);

    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    // 0 — закрыт; иначе момент (мс), до которого вызовы падают сразу
    private final AtomicLong openUntilMillis = new AtomicLong();
    // 0 — пробного запроса нет; иначе момент его старта. Пробный, который не вернул результат
    // (упал не FeignException), через OPEN_DURATION перестаёт считаться — пускаем новый
    private final AtomicLong probeStartedMillis = new AtomicLong();

    public OpenAiCircuitBreaker() {
        this(Clock.systemUTC());
    }

    OpenAiCircuitBreaker(Clock clock) {
        this.clock = clock;
    }

    /** Бросает AiUnavailableException, если предохранитель открыт и вызывать OpenAI сейчас не нужно. */
    public void checkAllowed() {
        long openUntil = openUntilMillis.get();
        if (openUntil == 0) return;
        long now = clock.millis();
        long probeStarted = probeStartedMillis.get();
        boolean probeRunning = probeStarted != 0 && now - probeStarted < OPEN_DURATION.toMillis();
        if (now < openUntil || probeRunning || !probeStartedMillis.compareAndSet(probeStarted, now)) {
            throw new AiUnavailableException("OpenAI circuit breaker is open", 0, null);
        }
    }

    /** OpenAI ответил (любой ответ, кроме 5xx): сбрасываем счётчик и закрываем. */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        if (openUntilMillis.getAndSet(0) != 0) {
            log.info("OpenAI circuit breaker closed");
        }
        probeStartedMillis.set(0);
    }

    /**
     * Результат неудачного вызова.
     * @param status HTTP-код FeignException; -1 или 0 — таймаут или сеть
     */
    public void recordFailure(int status) {
        if (!isOutage(status)) {
            recordSuccess();
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= FAILURE_THRESHOLD) {
            openUntilMillis.set(clock.millis() + OPEN_DURATION.toMillis());
            log.warn("OpenAI circuit breaker opened for {} s after {} failures in a row (last HTTP {})",
                    OPEN_DURATION.toSeconds(), failures, status);
        }
        probeStartedMillis.set(0);
    }

    static boolean isOutage(int status) {
        return status <= 0 || status >= 500;
    }
}
