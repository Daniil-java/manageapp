package com.kuklin.manageapp.bots.channelposter.configurations;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Настройки конвейера канала: сколько постов держать в работе, пороги AI, автопилот, сроки.
 * Значения — в application.yaml, блок channelposter.
 */
@Data
@Component
@ConfigurationProperties(prefix = "channelposter")
// Проверка при старте: значение вне диапазона или пропало из application.yaml — приложение не запустится
@Validated
public class ChannelPosterProperties {
    /** Сколько постов держим в работе (на проверке + в очереди). Дальше генерация ждёт. */
    @Positive
    private int pipelineTarget;
    /** Не больше стольких новых постов за один запуск генерации (каждый — текст + картинка). */
    @Positive
    private int postsPerRun;
    /** Если необработанных материалов (NEW + APPROVED) столько — новые не собираем. */
    @Positive
    private int maxBacklog;
    /** Оценка AI-фильтра (0–10), с которой материал идёт в работу. */
    @Min(0) @Max(10)
    private int approveScore;
    /** Оценка, с которой автопилот ставит непроверенный пост в очередь сам. */
    @Min(0) @Max(10)
    private int autopilotMinScore;
    /** Через сколько без ответа админа автопилот ставит пост в очередь. 0 — автопилот выключен. */
    @NotNull
    private Duration autopilotAfter;
    /** Материалы старше — не сохраняем и снимаем с фильтра/генерации. */
    @NotNull
    private Duration maxItemAge;
    /** Пост на проверке дольше — снимаем с конвейера (EXPIRED), чтобы не занимал место. */
    @NotNull
    private Duration reviewTtl;
    /** Час (время канала), в который на следующий день после опроса выходит пост с итогами. */
    @Min(0) @Max(23)
    private int pollResultsHour;
    /** Меньше голосов — пост с итогами не публикуем, админам — уведомление. */
    @PositiveOrZero
    private int pollResultsMinVoters;
}
