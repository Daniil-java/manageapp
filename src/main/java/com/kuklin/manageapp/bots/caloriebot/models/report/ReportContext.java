package com.kuklin.manageapp.bots.caloriebot.models.report;

import com.kuklin.manageapp.bots.caloriebot.models.airesponse.AiPatternAnalysisResponse;
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
