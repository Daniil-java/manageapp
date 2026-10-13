package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.components.services.WeightEntryService;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Тексты про вес: история взвешиваний (раньше — AnalyticsService.getWeightHistoryTextList).
 */
@Component
public class WeightTexts extends AbstractBotTexts {
    private final WeightEntryService weightEntryService;
    private final UserSettingsService userSettingsService;

    public WeightTexts(CalorieTexts texts, WeightEntryService weightEntryService, UserSettingsService userSettingsService) {
        super(texts);
        this.weightEntryService = weightEntryService;
        this.userSettingsService = userSettingsService;
    }

    /**
     * История взвешиваний с разницей между замерами и общим прогрессом.
     */
    public String buildWeightHistoryText(Long userId) {
        // Получаем список, отсортированный от старых к новым (Asc)
        List<WeightEntry> weightEntries = weightEntryService.getAllWeightHistory(userId);

        AppLanguage language = languageOf(userId);
        if (weightEntries.isEmpty()) {
            return texts.get(language, BotTextKeys.WEIGHT_HISTORY_EMPTY);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(texts.get(language, BotTextKeys.WEIGHT_HISTORY_TITLE)).append("\n");
        sb.append("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n");

        ZoneId zoneId = userSettingsService.getOrCreate(userId).getZoneId();
        for (int i = 0; i < weightEntries.size(); i++) {
            WeightEntry current = weightEntries.get(i);
            String trend = "";

            // Сравниваем с ПРЕДЫДУЩИМ элементом (i-1), так как идем от старых к новым
            if (i > 0) {
                WeightEntry previous = weightEntries.get(i - 1);
                double diff = current.getWeight().subtract(previous.getWeight()).doubleValue();

                if (diff > 0) {
                    trend = " 📈 <b>+" + String.format("%.1f", diff) + "</b>";
                } else if (diff < 0) {
                    trend = " 📉 <b>" + String.format("%.1f", diff) + "</b>";
                } else {
                    trend = " ↔️ 0.0";
                }
            }

            // Добавляем время, если в БД есть createdAt, чтобы различать замеры в один день
            String time = current.getCreatedAt() != null
                    ? " (" + current.getCreatedAt()
                    .atZone(zoneId)
                    .format(DateTimeFormatter.ofPattern("HH:mm")) + ")"
                    : "";

            sb.append(texts.get(language, BotTextKeys.WEIGHT_HISTORY_ENTRY,
                    current.getEntryDate() + time, String.valueOf(current.getWeight()), trend)).append("\n");
        }

        if (weightEntries.size() > 1) {
            WeightEntry latest = weightEntries.get(weightEntries.size() - 1);
            WeightEntry first = weightEntries.get(0);
            double totalDiff = latest.getWeight().subtract(first.getWeight()).doubleValue();

            sb.append("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n");
            String progEmoji = totalDiff <= 0 ? "✅" : "⚠️";
            sb.append(texts.get(language, BotTextKeys.WEIGHT_HISTORY_TOTAL,
                    progEmoji, (totalDiff > 0 ? "+" : "") + String.format("%.2f", totalDiff)));
        }

        return sb.toString();
    }
}
