package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.models.report.*;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportStatsCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UiAnalyticsService {

    private final DishService dishService;
    private final WeightEntryService weightService;
    private final UserNutritionProfileService profileService; // Для получения норм БЖУ и лимитов воды
    private final UserSettingsService userSettingsService; // Для определения ZoneId
    private final ReportSourceLoader reportSourceLoader; // Данные для календаря, тренда веса, расхода и привычек

    /**
     * Собирает комплексный дашборд для UI за указанный период.
     */
    public DashboardResponseDto getDashboardData(Long appUserId, LocalDate from, LocalDate to) {
        log.info("Сбор аналитики для пользователя {} с {} по {}", appUserId, from, to);

        // 1. Получаем настройки времени пользователя (важно для правильной группировки дней)
        ZoneId userZone = userSettingsService.getZoneId(appUserId);
        LocalDate today = LocalDate.now(userZone);

        // 2. Загружаем исходные данные из БД за период
        List<Dish> dishes = dishService.getDishesByPeriod(appUserId, from, to);
        List<WeightEntry> weights = weightService.getAllWeightByPeriod(appUserId, from, to);
        UserNutritionProfile profile = profileService.getOrCreateProfile(appUserId);

        // 3. Расчет прогресса за сегодняшний день
        DailyProgressDto todayProgress = buildTodayProgress(dishes, profile, today, userZone);

        // 4. Расчет периодической статистики (Калории и БЖУ по дням)
        List<PeriodStatDto> periodStats = buildPeriodStats(dishes, from, to, userZone);

        // 5. Расчет распределения по категориям
        List<CategoryStatDto> categoryDistribution = buildCategoryDistribution(dishes);

        // 6. Расчет тренда изменения веса
        List<WeightPointDto> weightTrend = weights.stream()
                .map(w -> new WeightPointDto()
                        // Дата взвешивания, а не записи: вес можно внести задним числом
                        .setDate(w.getEntryDate())
                        .setWeight(w.getWeight()))
                .sorted(Comparator.comparing(WeightPointDto::getDate))
                .collect(Collectors.toList());

        // 7. Расчет часового ритма питания (0-23 ч)
        Map<Integer, Integer> hourlyRhythm = buildHourlyRhythm(dishes, userZone);

        // 8. Календарь нормы, вес с трендом и прогнозом, реальный расход, привычки — как в PDF-отчёте
        ReportStats stats = ReportStatsCalculator.buildStats(reportSourceLoader.loadPeriod(from, to, appUserId));

        // Собираем все воедино
        return new DashboardResponseDto()
                .setAnalytics(InsightsAnalyticsDto.of(stats))
                .setTodayProgress(todayProgress)
                .setPeriodStats(periodStats)
                .setCategoryDistribution(categoryDistribution)
                .setWeightTrend(weightTrend)
                .setHourlyRhythm(hourlyRhythm);
    }

    private DailyProgressDto buildTodayProgress(List<Dish> dishes, UserNutritionProfile profile, LocalDate today, ZoneId zoneId) {
        // Фильтруем блюда, съеденные именно сегодня по времени пользователя
        List<Dish> todayDishes = dishes.stream()
                .filter(d -> d.getCreated().atZone(zoneId).toLocalDate().equals(today))
                .toList();

        int caloriesFact = todayDishes.stream().mapToInt(d -> nvl(d.getCalories())).sum();
        int proteinsFact = todayDishes.stream().mapToInt(d -> nvl(d.getProteins())).sum();
        int fatsFact = todayDishes.stream().mapToInt(d -> nvl(d.getFats())).sum();
        int carbsFact = todayDishes.stream().mapToInt(d -> nvl(d.getCarbohydrates())).sum();

        DailyProgressDto dto = new DailyProgressDto()
                .setCaloriesFact(caloriesFact)
                .setProteinsFact(proteinsFact)
                .setFatsFact(fatsFact)
                .setCarbsFact(carbsFact)
                .setCarbsTarget(profile.getCarbsNormGramsPerDay())
                .setFatsTarget(profile.getFatsNormGramsPerDay())
                .setProteinsTarget(profile.getProteinsNormGramsPerDay())
                .setCaloriesTarget(profile.getCaloriesNormPerDay())
                ;

        return dto;
    }

    private List<PeriodStatDto> buildPeriodStats(List<Dish> dishes, LocalDate from, LocalDate to, ZoneId zoneId) {
        // Группируем блюда по дате (в зоне пользователя)
        Map<LocalDate, List<Dish>> groupedByDate = dishes.stream()
                .collect(Collectors.groupingBy(d -> d.getCreated().atZone(zoneId).toLocalDate()));

        List<PeriodStatDto> result = new ArrayList<>();

        // Перебираем все даты диапазона, чтобы не было пропусков на графике
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<Dish> dayDishes = groupedByDate.getOrDefault(date, Collections.emptyList());

            int calories = dayDishes.stream().mapToInt(d -> nvl(d.getCalories())).sum();
            int proteins = dayDishes.stream().mapToInt(d -> nvl(d.getProteins())).sum();
            int fats = dayDishes.stream().mapToInt(d -> nvl(d.getFats())).sum();
            int carbs = dayDishes.stream().mapToInt(d -> nvl(d.getCarbohydrates())).sum();

            result.add(new PeriodStatDto()
                    .setDate(date)
                    .setCalories(calories)
                    .setProteins(proteins)
                    .setFats(fats)
                    .setCarbs(carbs));
        }
        return result;
    }

    private List<CategoryStatDto> buildCategoryDistribution(List<Dish> dishes) {
        int totalCalories = dishes.stream().mapToInt(d -> nvl(d.getCalories())).sum();
        if (totalCalories == 0) {
            return Collections.emptyList();
        }

        // Группируем по имени категории (или enum, если используется FoodCategory)
        Map<String, Integer> caloriesByCategory = dishes.stream()
                .filter(d -> d.getCategory() != null)
                .collect(Collectors.groupingBy(
                        d -> d.getCategory().name(),
                        Collectors.summingInt(d -> nvl(d.getCalories()))
                ));

        return caloriesByCategory.entrySet().stream()
                .map(entry -> {
                    double percentage = (double) entry.getValue() / totalCalories * 100;
                    return new CategoryStatDto()
                            .setCategoryName(entry.getKey())
                            .setCalories(entry.getValue())
                            .setPercentage(Math.round(percentage * 10.0) / 10.0); // Округление до 1 знака
                })
                .sorted(Comparator.comparingInt(CategoryStatDto::getCalories).reversed())
                .collect(Collectors.toList());
    }

    private Map<Integer, Integer> buildHourlyRhythm(List<Dish> dishes, ZoneId zoneId) {
        // Инициализируем карту всеми часами суток со значением 0
        Map<Integer, Integer> hourlyMap = new HashMap<>();
        for (int i = 0; i < 24; i++) {
            hourlyMap.put(i, 0);
        }

        // Суммируем калории по часам с учетом таймзоны
        dishes.forEach(d -> {
            int hour = d.getCreated().atZone(zoneId).getHour();
            hourlyMap.put(hour, hourlyMap.get(hour) + nvl(d.getCalories()));
        });

        return hourlyMap;
    }

    /** Пустое поле блюда (калории, БЖУ) — 0, а не NullPointerException на весь дашборд */
    private static int nvl(Integer value) {
        return value != null ? value : 0;
    }
}
