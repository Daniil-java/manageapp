package com.kuklin.manageapp.bots.caloriebot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiPatternAnalysisResponse {
    private List<String> topGeneralProducts;
    private List<String> topUnhealthyProducts;
    private List<String> topCalorieSources;
    private MacroBalanceDTO macroBalance;
    private List<String> detectedHabits;
    private List<String> behavioralPatterns;
    private List<String> recommendations;
    private String psychologicalRootCause;

    public String toHtml() {
        StringBuilder sb = new StringBuilder();
        if (macroBalance != null) {
            sb.append("<div style='margin-bottom: 20px;'><h3>⚖️ Баланс нутриентов</h3>")
                    .append("<div class='macro-box'><strong>Белки</strong><br/>").append(String.format("%.1f%%", macroBalance.getProteinPercentage())).append("</div>")
                    .append("<div class='macro-box'><strong>Жиры</strong><br/>").append(String.format("%.1f%%", macroBalance.getFatPercentage())).append("</div>")
                    .append("<div class='macro-box'><strong>Углеводы</strong><br/>").append(String.format("%.1f%%", macroBalance.getCarbPercentage())).append("</div>")
                    .append("<p><strong>Анализ:</strong> ").append(macroBalance.getImbalanceAnalysis()).append("</p></div>");
        }
        appendListHtml(sb, "🍎 Основные продукты", topGeneralProducts);
        appendListHtml(sb, "⚠️ Обратить внимание", topUnhealthyProducts);
        appendListHtml(sb, "🔥 Главные источники калорий", topCalorieSources);
        appendListHtml(sb, "🧠 Выявленные привычки", detectedHabits);
        appendListHtml(sb, "🔄 Поведенческие шаблоны", behavioralPatterns);
        if (psychologicalRootCause != null) sb.append("<h3>🕵️ Психологическая причина</h3><p>").append(psychologicalRootCause).append("</p>");
        appendListHtml(sb, "💡 Рекомендации", recommendations);
        return sb.toString();
    }

    private void appendListHtml(StringBuilder sb, String title, List<String> list) {
        if (list != null && !list.isEmpty()) {
            sb.append("<h3>").append(title).append("</h3><ul>");
            list.forEach(item -> sb.append("<li>").append(item).append("</li>"));
            sb.append("</ul>");
        }
    }
}
