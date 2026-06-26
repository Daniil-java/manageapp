package com.kuklin.manageapp.bots.caloriebot.models.report;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class DashboardResponseDto {
    private DailyProgressDto todayProgress;
    private List<PeriodStatDto> periodStats; // Для графика по дням
    private List<CategoryStatDto> categoryDistribution; // Для пирога категорий
    private List<WeightPointDto> weightTrend; // Для графика веса
    private Map<Integer, Integer> hourlyRhythm; // Час -> Калории
}
