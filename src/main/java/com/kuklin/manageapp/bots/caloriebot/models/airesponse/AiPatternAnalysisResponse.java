package com.kuklin.manageapp.bots.caloriebot.models.airesponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kuklin.manageapp.bots.caloriebot.i18n.PdfTexts;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static com.kuklin.manageapp.bots.caloriebot.i18n.PdfTextKeys.*;
import static com.kuklin.manageapp.bots.caloriebot.utils.ReportUtils.escapeHtml;

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

    /**
     * Блок «Анализ пищевого поведения» для месячного PDF. Заголовки — из словаря pdf_* на языке отчёта,
     * текст ИИ экранируется: «<» в ответе ломал бы разметку PDF.
     */
    public String toHtml(PdfTexts texts) {
        StringBuilder sb = new StringBuilder();
        if (macroBalance != null) {
            sb.append("<div style='margin-bottom: 20px;'><h3>").append(escapeHtml(texts.get(ANALYSIS_MACRO_BALANCE))).append("</h3>");
            appendMacroBox(sb, texts.get(COL_PROTEINS), macroBalance.getProteinPercentage());
            appendMacroBox(sb, texts.get(COL_FATS), macroBalance.getFatPercentage());
            appendMacroBox(sb, texts.get(COL_CARBS), macroBalance.getCarbPercentage());
            sb.append("<p><strong>").append(escapeHtml(texts.get(ANALYSIS_SUMMARY))).append("</strong> ")
                    .append(escapeHtml(macroBalance.getImbalanceAnalysis())).append("</p></div>");
        }
        appendListHtml(sb, texts.get(ANALYSIS_TOP_PRODUCTS), topGeneralProducts);
        appendListHtml(sb, texts.get(ANALYSIS_UNHEALTHY), topUnhealthyProducts);
        appendListHtml(sb, texts.get(ANALYSIS_CALORIE_SOURCES), topCalorieSources);
        appendListHtml(sb, texts.get(ANALYSIS_HABITS), detectedHabits);
        appendListHtml(sb, texts.get(ANALYSIS_PATTERNS), behavioralPatterns);
        if (psychologicalRootCause != null) {
            sb.append("<h3>").append(escapeHtml(texts.get(ANALYSIS_ROOT_CAUSE))).append("</h3><p>")
                    .append(escapeHtml(psychologicalRootCause)).append("</p>");
        }
        appendListHtml(sb, texts.get(ANALYSIS_RECOMMENDATIONS), recommendations);
        return sb.toString();
    }

    private void appendMacroBox(StringBuilder sb, String title, double percentage) {
        sb.append("<div class='macro-box'><strong>").append(escapeHtml(title)).append("</strong><br/>")
                .append(String.format("%.1f%%", percentage)).append("</div>");
    }

    private void appendListHtml(StringBuilder sb, String title, List<String> list) {
        if (list != null && !list.isEmpty()) {
            sb.append("<h3>").append(escapeHtml(title)).append("</h3><ul>");
            list.forEach(item -> sb.append("<li>").append(escapeHtml(item)).append("</li>"));
            sb.append("</ul>");
        }
    }
}
