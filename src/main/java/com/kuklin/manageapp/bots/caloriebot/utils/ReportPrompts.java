package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;

/**
 * Набор промптов отчётов на языке пользователя: английский — для EN, русский — для остальных
 * (новый язык без своих промптов получит русские, строка «язык ответа» в них всё равно есть).
 */
public record ReportPrompts(String persona,
                            String todayAnalysis,
                            String insightSummary,
                            String insightPatterns,
                            String report,
                            String periodLabelFormat) {

    static final ReportPrompts RU = new ReportPrompts(
            ReportCalorieBotPrompts.AI_PERSONA,
            ReportCalorieBotPrompts.AI_REQUEST_TODAY_ANALYSIS,
            ReportCalorieBotPrompts.AI_REQUEST_INSIGHT_SUMMARY,
            ReportCalorieBotPrompts.AI_REQUEST_INSIGHT_PATTERNS,
            ReportCalorieBotPrompts.AI_REQUEST_REPORT,
            "последние %d дней");

    static final ReportPrompts EN = new ReportPrompts(
            ReportCalorieBotPromptsEn.AI_PERSONA,
            ReportCalorieBotPromptsEn.AI_REQUEST_TODAY_ANALYSIS,
            ReportCalorieBotPromptsEn.AI_REQUEST_INSIGHT_SUMMARY,
            ReportCalorieBotPromptsEn.AI_REQUEST_INSIGHT_PATTERNS,
            ReportCalorieBotPromptsEn.AI_REQUEST_REPORT,
            "last %d days");

    public static ReportPrompts of(AppLanguage language) {
        return language == AppLanguage.EN ? EN : RU;
    }

    /** Период словами для промптов Insights: «последние 7 дней» / «last 7 days». */
    public String buildPeriodLabel(int days) {
        return String.format(periodLabelFormat, days);
    }
}
