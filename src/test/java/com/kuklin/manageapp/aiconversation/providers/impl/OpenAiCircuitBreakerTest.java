package com.kuklin.manageapp.aiconversation.providers.impl;

import com.kuklin.manageapp.aiconversation.models.AiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAI лежит — после серии сбоев не ждём таймаут на каждый запрос, а сразу «ИИ недоступен».
 */
class OpenAiCircuitBreakerTest {

    private MutableClock clock;
    private OpenAiCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        breaker = new OpenAiCircuitBreaker(clock);
    }

    @Test
    void opensAfterThresholdOfOutages() {
        failTimes(OpenAiCircuitBreaker.FAILURE_THRESHOLD - 1, -1);
        assertThatCode(breaker::checkAllowed).doesNotThrowAnyException();

        breaker.recordFailure(503);
        assertThatThrownBy(breaker::checkAllowed).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void clientErrorsDoNotOpenAndResetCounter() {
        failTimes(OpenAiCircuitBreaker.FAILURE_THRESHOLD - 1, 500);
        breaker.recordFailure(429);
        failTimes(OpenAiCircuitBreaker.FAILURE_THRESHOLD - 1, 500);

        assertThatCode(breaker::checkAllowed).doesNotThrowAnyException();
    }

    @Test
    void afterOpenDurationLetsSingleProbeThrough() {
        failTimes(OpenAiCircuitBreaker.FAILURE_THRESHOLD, 0);
        clock.advance(OpenAiCircuitBreaker.OPEN_DURATION.toMillis());

        assertThatCode(breaker::checkAllowed).doesNotThrowAnyException();
        // пока пробный не вернулся — остальные падают сразу
        assertThatThrownBy(breaker::checkAllowed).isInstanceOf(AiUnavailableException.class);

        breaker.recordSuccess();
        assertThatCode(breaker::checkAllowed).doesNotThrowAnyException();
        assertThatCode(breaker::checkAllowed).doesNotThrowAnyException();
    }

    @Test
    void failedProbeReopens() {
        failTimes(OpenAiCircuitBreaker.FAILURE_THRESHOLD, 0);
        clock.advance(OpenAiCircuitBreaker.OPEN_DURATION.toMillis());
        breaker.checkAllowed();

        breaker.recordFailure(-1);

        assertThatThrownBy(breaker::checkAllowed).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void lostProbeIsReplacedAfterOpenDuration() {
        failTimes(OpenAiCircuitBreaker.FAILURE_THRESHOLD, 0);
        clock.advance(OpenAiCircuitBreaker.OPEN_DURATION.toMillis());
        breaker.checkAllowed(); // пробный ушёл и не сообщил результат

        clock.advance(OpenAiCircuitBreaker.OPEN_DURATION.toMillis());

        assertThatCode(breaker::checkAllowed).doesNotThrowAnyException();
    }

    private void failTimes(int times, int status) {
        for (int i = 0; i < times; i++) breaker.recordFailure(status);
    }

    private static class MutableClock extends Clock {
        private long millis = 1_000_000;

        void advance(long ms) { millis += ms; }

        @Override public long millis() { return millis; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
