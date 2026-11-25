package com.kuklin.manageapp.bots.metrics.services;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Защита от шквала одинаковых ошибок (упала БД — каждый шедулер и запрос пишет своё):
 * первая ошибка уходит сразу, повторы той же ошибки в течение окна только считаются,
 * по окончании окна — сводка «повторилась N раз». Пока ошибка продолжается — сводка каждое окно,
 * полное сообщение снова — только после окна без повторов.
 */
@Component
public class ErrorStormGuard {

    static final Duration WINDOW = Duration.ofMinutes(2);

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** true — отправить сообщение; false — повтор внутри окна, только посчитан. */
    public boolean tryAcquire(String signature, String title, Instant now) {
        boolean[] send = {false};
        windows.compute(signature, (key, window) -> {
            if (window == null) {
                send[0] = true;
                return new Window(now, 0, title);
            }
            return new Window(window.start(), window.repeats() + 1, window.title());
        });
        return send[0];
    }

    /** Закрывает окна, которые прошли: сводки по ошибкам с повторами; окна без повторов удаляются. */
    public List<Summary> flush(Instant now) {
        List<Summary> summaries = new ArrayList<>();
        for (String signature : windows.keySet()) {
            windows.computeIfPresent(signature, (key, window) -> {
                if (Duration.between(window.start(), now).compareTo(WINDOW) < 0) return window;
                if (window.repeats() == 0) return null;
                summaries.add(new Summary(window.title(), window.repeats()));
                return new Window(now, 0, window.title());
            });
        }
        return summaries;
    }

    public record Summary(String title, int repeats) {
    }

    private record Window(Instant start, int repeats, String title) {
    }
}
