package com.kuklin.manageapp.bots.caloriebot.models;

import tech.tablesaw.api.Table;

public record ReportContext(String aiSummary,
                            Table dailyTable,
                            Table weeklyTable,
                            Table weightTable,
                            AiPatternAnalysisResponse patternAnalysis,
                            String categoryChartHtml,
                            String timingChartHtml
) {
}
