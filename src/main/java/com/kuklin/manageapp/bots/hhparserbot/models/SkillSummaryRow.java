package com.kuklin.manageapp.bots.hhparserbot.models;

public record SkillSummaryRow(
        String skillName,
        long aiCount,
        long apiCount,
        long total,
        double aiShare
) {

    public SkillSummaryRow(String skillName, long aiCount, long apiCount, long total) {
        this(skillName,
                aiCount,
                apiCount,
                total,
                total == 0 ? 0.0 : (double) aiCount / (double) total);
    }

}
