package com.kuklin.manageapp.bots.metrics.services;

import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiLog;
import com.kuklin.manageapp.bots.metrics.repositories.MetricsAiLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsAiLogService {
    private final MetricsAiLogRepository metricsAiLogRepository;
    private static final ZoneId HO_CHI_MINH_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public MetricsAiLog getTodayLog() {
        LocalDate today = LocalDate.now(HO_CHI_MINH_ZONE);

        MetricsAiLog logForToday = metricsAiLogRepository
                .findByDate(today)
                .orElseGet(() -> {
                    MetricsAiLog created = new MetricsAiLog()
                            .setDate(today)
                            .setTotalAiRequestCount(0L)
                            .setOpenAiRequestCount(0L)
                            .setGeminiAiRequestCount(0L)
                            .setClaudeAiRequestCount(0L)
                            .setDeepSeekAiRequestCount(0L)
                            .setYandexAiRequestCount(0L);

                    return metricsAiLogRepository.save(created);
                });
        return logForToday;
    }

    /**
     * Увеличивает счётчик по провайдеру и общий счётчик за сегодняшний день.
     * Потокобезопасно за счёт пессимистической блокировки строки.
     */
    @Transactional
    public void incrementForProvider(ProviderVariant provider) {
        // Берём строку за сегодня с блокировкой PESSIMISTIC_WRITE
        MetricsAiLog logForToday = getTodayLog();

        // общий счётчик
        logForToday.setTotalAiRequestCount(increment(logForToday.getTotalAiRequestCount()));

        // счётчик по конкретному провайдеру
        if (provider != null) {
            switch (provider) {
                case OPENAI -> logForToday.setOpenAiRequestCount(
                        increment(logForToday.getOpenAiRequestCount())
                );
                case GEMINI -> logForToday.setGeminiAiRequestCount(
                        increment(logForToday.getGeminiAiRequestCount())
                );
                case CLAUDE -> logForToday.setClaudeAiRequestCount(
                        increment(logForToday.getClaudeAiRequestCount())
                );
                case DEEPSEEK -> logForToday.setDeepSeekAiRequestCount(
                        increment(logForToday.getDeepSeekAiRequestCount())
                );
                default -> {
                    // на случай, если появится новый провайдер, о котором мы ещё не знаем
                    log.debug("Provider {} not explicitly handled in MetricsAiLogService", provider);
                }
            }
        }
         metricsAiLogRepository.save(logForToday);
    }

    private long increment(Long current) {
        return current == null ? 1L : current + 1L;
    }

}
