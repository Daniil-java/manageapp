package com.kuklin.manageapp.bots.metrics.services;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorStormGuardTest {

    private final ErrorStormGuard guard = new ErrorStormGuard();
    private final Instant t0 = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void firstErrorIsSentRepeatsInsideWindowAreCounted() {
        assertThat(guard.tryAcquire("db", "DB down", t0)).isTrue();
        assertThat(guard.tryAcquire("db", "DB down", t0.plusSeconds(10))).isFalse();
        assertThat(guard.tryAcquire("db", "DB down", t0.plusSeconds(50))).isFalse();
        // другая ошибка — своё окно
        assertThat(guard.tryAcquire("hh", "HH 403", t0.plusSeconds(60))).isTrue();

        assertThat(guard.flush(t0.plusSeconds(90))).isEmpty();
        assertThat(guard.flush(t0.plus(ErrorStormGuard.WINDOW)))
                .containsExactly(new ErrorStormGuard.Summary("DB down", 2));
    }

    @Test
    void ongoingStormGivesSummaryEveryWindowNotFullMessages() {
        guard.tryAcquire("db", "DB down", t0);
        guard.tryAcquire("db", "DB down", t0.plusSeconds(30));
        guard.flush(t0.plus(ErrorStormGuard.WINDOW));

        // ошибка продолжается — снова только счётчик
        Instant later = t0.plus(ErrorStormGuard.WINDOW).plusSeconds(5);
        assertThat(guard.tryAcquire("db", "DB down", later)).isFalse();
        assertThat(guard.flush(later.plus(ErrorStormGuard.WINDOW)))
                .containsExactly(new ErrorStormGuard.Summary("DB down", 1));
    }

    @Test
    void afterQuietWindowErrorIsSentAgain() {
        guard.tryAcquire("db", "DB down", t0);
        assertThat(guard.flush(t0.plus(ErrorStormGuard.WINDOW))).isEmpty();

        assertThat(guard.tryAcquire("db", "DB down", t0.plus(Duration.ofMinutes(5)))).isTrue();
    }
}
