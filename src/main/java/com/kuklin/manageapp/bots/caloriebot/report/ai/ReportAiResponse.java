package com.kuklin.manageapp.bots.caloriebot.report.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Ответ ИИ для PDF-отчёта: главная фраза периода, шаблоны поведения и замены продуктов.
 * Цифры ИИ получает уже посчитанными — здесь только их объяснение.
 *
 * @param headline одна фраза для обложки
 * @param patterns карточки шаблонов поведения (как на странице Insights)
 * @param swaps    конкретные замены продуктов на следующую неделю
 * @param nutrientsTip совет по клетчатке и микронутриентам, 1–2 предложения; null — данных о них нет
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportAiResponse(String headline, List<Pattern> patterns, List<Swap> swaps, String nutrientsTip) {

    public ReportAiResponse(String headline, List<Pattern> patterns, List<Swap> swaps) {
        this(headline, patterns, swaps, null);
    }

    public static ReportAiResponse empty() {
        return new ReportAiResponse(null, List.of(), List.of(), null);
    }

    /**
     * @param sentiment positive / neutral / negative — цвет полоски карточки
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pattern(String emoji, String title, String description, String impact, String sentiment) {
    }

    /**
     * @param from   что сейчас ест пользователь
     * @param to     на что заменить
     * @param saving экономия: «−130 ккал/день»
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Swap(String from, String to, String saving) {
    }
}
