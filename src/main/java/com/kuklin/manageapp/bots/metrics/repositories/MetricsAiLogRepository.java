package com.kuklin.manageapp.bots.metrics.repositories;

import com.kuklin.manageapp.bots.metrics.entities.MetricsAiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MetricsAiLogRepository extends JpaRepository<MetricsAiLog, Long> {

    Optional<MetricsAiLog> findByDate(LocalDate date);

    /**
     * Создаёт строку за день или увеличивает счётчики — одной командой (уникальный ключ по date).
     * Параллельные вызовы не теряют инкременты и не падают на создании строки.
     * Счётчики провайдеров — 1 или 0.
     */
    @Modifying
    @Query(value = """
            INSERT INTO metrics_ai_log (date, total_ai_request_count, open_ai_request_count,
                                        gemini_ai_request_count, claude_ai_request_count,
                                        deep_seek_ai_request_count, yandex_ai_request_count)
            VALUES (:date, 1, :openAi, :gemini, :claude, :deepSeek, 0)
            ON CONFLICT (date) DO UPDATE SET
                total_ai_request_count     = metrics_ai_log.total_ai_request_count + 1,
                open_ai_request_count      = metrics_ai_log.open_ai_request_count + EXCLUDED.open_ai_request_count,
                gemini_ai_request_count    = metrics_ai_log.gemini_ai_request_count + EXCLUDED.gemini_ai_request_count,
                claude_ai_request_count    = metrics_ai_log.claude_ai_request_count + EXCLUDED.claude_ai_request_count,
                deep_seek_ai_request_count = metrics_ai_log.deep_seek_ai_request_count + EXCLUDED.deep_seek_ai_request_count
            """, nativeQuery = true)
    int increment(@Param("date") LocalDate date,
                  @Param("openAi") int openAi,
                  @Param("gemini") int gemini,
                  @Param("claude") int claude,
                  @Param("deepSeek") int deepSeek);
}
