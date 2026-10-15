package com.kuklin.manageapp.bots.channelposter.configurations;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Настройки конвейера канала: сколько постов держать в работе, пороги AI, автопилот, сроки.
 * Значения — в application.yaml, блок channelposter.
 */
@Data
@Component
@ConfigurationProperties(prefix = "channelposter")
public class ChannelPosterProperties {
    /** Сколько постов держим в работе (на проверке + в очереди). Дальше генерация ждёт. */
    private int pipelineTarget;
    /** Не больше стольких новых постов за один запуск генерации (каждый — текст + картинка). */
    private int postsPerRun;
    /** Если необработанных материалов (NEW + APPROVED) столько — новые не собираем. */
    private int maxBacklog;
    /** Оценка AI-фильтра (0–10), с которой материал идёт в работу. */
    private int approveScore;
    /** Оценка, с которой автопилот ставит непроверенный пост в очередь сам. */
    private int autopilotMinScore;
    /** Через сколько без ответа админа автопилот ставит пост в очередь. 0 — автопилот выключен. */
    private Duration autopilotAfter;
    /** Материалы старше — не сохраняем и снимаем с фильтра/генерации. */
    private Duration maxItemAge;
    /** Пост на проверке дольше — снимаем с конвейера (EXPIRED), чтобы не занимал место. */
    private Duration reviewTtl;
}
