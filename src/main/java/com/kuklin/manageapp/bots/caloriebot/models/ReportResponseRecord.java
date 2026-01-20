package com.kuklin.manageapp.bots.caloriebot.models;

import tech.tablesaw.api.Table;

public record ReportResponseRecord(Table table, String text, String aiRequest) {
}
