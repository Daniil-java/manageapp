package com.kuklin.manageapp.bots.caloriebot.models.report;

import tech.tablesaw.api.Table;

public record ReportResponseRecord(Table table, String text, String aiRequest) {
}
