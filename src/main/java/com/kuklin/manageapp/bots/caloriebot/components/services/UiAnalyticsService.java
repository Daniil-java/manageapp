package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.models.report.*;
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

    /**
     * Собирает комплексный дашборд для UI за указанный период.
     */
    public DashboardResponseDto getDashboardData(Long appUserId, LocalDate from, LocalDate to) {
        log.info("Сбор аналитики для пользователя {} с {} по {}", appUserId, from, to);

        // 1. Получаем настройки времени пользователя (важно для правильной группировки дней)
        UserSettings userSettings = userSettingsService.getOrCreate(appUserId);
        ZoneId userZone = userSettings.getZoneId();
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
                        .setDate(w.getCreatedAt().atZone(userZone).toLocalDate())
                        .setWeight(w.getWeight()))
                .sorted(Comparator.comparing(WeightPointDto::getDate))
                .collect(Collectors.toList());

        // 7. Расчет часового ритма питания (0-23 ч)
        Map<Integer, Integer> hourlyRhythm = buildHourlyRhythm(dishes, userZone);

        // Собираем все воедино
        return new DashboardResponseDto()
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

        int caloriesFact = todayDishes.stream().mapToInt(Dish::getCalories).sum();
        int proteinsFact = todayDishes.stream().mapToInt(Dish::getProteins).sum();
        int fatsFact = todayDishes.stream().mapToInt(Dish::getFats).sum();
        int carbsFact = todayDishes.stream().mapToInt(Dish::getCarbohydrates).sum();

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

            int calories = dayDishes.stream().mapToInt(Dish::getCalories).sum();
            int proteins = dayDishes.stream().mapToInt(Dish::getProteins).sum();
            int fats = dayDishes.stream().mapToInt(Dish::getFats).sum();
            int carbs = dayDishes.stream().mapToInt(Dish::getCarbohydrates).sum();

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
        int totalCalories = dishes.stream().mapToInt(Dish::getCalories).sum();
        if (totalCalories == 0) {
            return Collections.emptyList();
        }

        // Группируем по имени категории (или enum, если используется FoodCategory)
        Map<String, Integer> caloriesByCategory = dishes.stream()
                .filter(d -> d.getCategory() != null)
                .collect(Collectors.groupingBy(
                        d -> d.getCategory().name(),
                        Collectors.summingInt(Dish::getCalories)
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
            hourlyMap.put(hour, hourlyMap.get(hour) + d.getCalories());
        });

        return hourlyMap;
    }
}
