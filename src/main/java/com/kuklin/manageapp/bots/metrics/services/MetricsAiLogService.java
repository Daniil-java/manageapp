package com.kuklin.manageapp.bots.metrics.services;

import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.bots.metrics.repositories.MetricsAiLogRepository;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsAiLogService {
    private final MetricsAiLogRepository metricsAiLogRepository;
    private static final ZoneId HO_CHI_MINH_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Счётчики за день date (по Хошимину) — только для чтения. Если запросов не было, строку не создаём,
     * возвращаем нулевые счётчики (строку создаёт {@link #incrementForProvider}).
     */
    public MetricsAiLog getLog(LocalDate date) {
        return metricsAiLogRepository
                .findByDate(date)
                .orElseGet(() -> new MetricsAiLog()
                        .setDate(date)
                        .setTotalAiRequestCount(0L)
                        .setOpenAiRequestCount(0L)
                        .setGeminiAiRequestCount(0L)
                        .setClaudeAiRequestCount(0L)
                        .setDeepSeekAiRequestCount(0L)
                        .setYandexAiRequestCount(0L));
    }

    /**
     * Увеличивает счётчик по провайдеру и общий счётчик за сегодняшний день.
     * <p>
     * Вызывается перед каждым запросом к ИИ из всех ботов, а строка за день одна на всех.
     * Поэтому:
     * <ul>
     * <li>инкремент — один атомарный upsert в БД: параллельные запросы не теряют обновления;</li>
     * <li>REQUIRES_NEW — коммит сразу. Если вызывающий код в транзакции, блокировка строки
     * не висит до её конца (пока ждём ИИ, рендерим PDF и т.д.) и не тормозит запросы к ИИ остальных.</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementForProvider(ProviderVariant provider) {
        metricsAiLogRepository.increment(
                LocalDate.now(HO_CHI_MINH_ZONE),
                provider == ProviderVariant.OPENAI ? 1 : 0,
                provider == ProviderVariant.GEMINI ? 1 : 0,
                provider == ProviderVariant.CLAUDE ? 1 : 0,
                provider == ProviderVariant.DEEPSEEK ? 1 : 0
        );
    }
}
