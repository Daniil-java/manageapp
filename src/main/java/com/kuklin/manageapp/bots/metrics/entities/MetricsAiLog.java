package com.kuklin.manageapp.bots.metrics.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;

@Entity
@Table(name = "metrics_ai_log")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class MetricsAiLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long totalAiRequestCount;
    private Long openAiRequestCount;
    private Long geminiAiRequestCount;
    private Long claudeAiRequestCount;
    private Long deepSeekAiRequestCount;
    private Long yandexAiRequestCount;

    private LocalDate date;

    public String getStringForTelegram() {
        StringBuilder sb = new StringBuilder();

        sb.append("📊 Статистика AI-запросов за ").append(date).append("\n")
                .append("Всего запросов: ").append(safe(totalAiRequestCount)).append("\n")
                .append("OpenAI: ").append(safe(openAiRequestCount)).append("\n")
                .append("Gemini: ").append(safe(geminiAiRequestCount)).append("\n")
                .append("Claude: ").append(safe(claudeAiRequestCount)).append("\n")
                .append("DeepSeek: ").append(safe(deepSeekAiRequestCount)).append("\n")
                .append("YandexGPT: ").append(safe(yandexAiRequestCount));

        return sb.toString();
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

}
