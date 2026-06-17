package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfileEntry;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Утилитарный класс для трансформации данных рациона и веса в аналитические таблицы.
 * Использует Tablesaw для построения колоночных структур данных.
 */
public class ReportUtils {

    /**
     * Формирует агрегированную таблицу по неделям.
     * Помогает AI и пользователю увидеть долгосрочные тренды, сглаживая ежедневные колебания.
     */
    public static Table buildPeriodReportPerWeek(List<Dish> dishes, List<WeightEntry> weightEntries, UserSettings userSettings, Instant from, Instant to) {
        ZoneId zoneId = userSettings.getZoneId();

        // Индексируем вес по датам для быстрого доступа
        Map<LocalDate, BigDecimal> weightByDate = weightEntries.stream()
                .collect(Collectors.toMap(WeightEntry::getEntryDate, WeightEntry::getWeightKg, (a, b) -> b));

        // Группируем блюда по датам с учетом часового пояса пользователя
        Map<LocalDate, List<Dish>> dishesByDate = dishes.stream()
                .collect(Collectors.groupingBy(d -> d.getCreated().atZone(zoneId).toLocalDate()));

        LocalDate startDate = from.atZone(zoneId).toLocalDate();
        LocalDate endDate = to.atZone(zoneId).toLocalDate();

        // Инициализация колонок будущей таблицы
        StringColumn weekCol = StringColumn.create("Неделя");
        StringColumn caloriesCol = StringColumn.create("Ср. Ккал");
        StringColumn proteinsCol = StringColumn.create("Ср. Белки");
        StringColumn fatsCol = StringColumn.create("Ср. Жиры");
        StringColumn carbsCol = StringColumn.create("Ср. Углеводы");
        StringColumn weightCol = StringColumn.create("Ср. Вес");

        // Начинаем расчет с понедельника первой недели периода
        LocalDate weekStart = startDate.with(DayOfWeek.MONDAY);

        while (!weekStart.isAfter(endDate)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            int daysWithFood = 0, caloriesSum = 0, proteinsSum = 0, fatsSum = 0, carbsSum = 0;
            List<BigDecimal> weekWeights = new ArrayList<>();

            // Проходим по каждому дню внутри текущей недели
            for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
                if (date.isBefore(startDate) || date.isAfter(endDate)) continue;

                List<Dish> dayDishes = dishesByDate.get(date);
                if (dayDishes == null || dayDishes.isEmpty()) continue;

                daysWithFood++;
                for (Dish d : dayDishes) {
                    caloriesSum += nvl(d.getCalories());
                    proteinsSum += nvl(d.getProteins());
                    fatsSum += nvl(d.getFats());
                    carbsSum += nvl(d.getCarbohydrates());
                }

                BigDecimal w = weightByDate.get(date);
                if (w != null) weekWeights.add(w);
            }

            String weekLabel = weekStart + " – " + weekEnd;

            // Если данных в неделе слишком мало (меньше 3-х дней с записями), не считаем среднее,
            // чтобы не искажать аналитику (особенно важно для AI-прогнозов).
            if (daysWithFood < 3) {
                weekCol.append(weekLabel); caloriesCol.append("—"); proteinsCol.append("—"); fatsCol.append("—"); carbsCol.append("—"); weightCol.append("—");
            } else {
                weekCol.append(weekLabel);
                caloriesCol.append(String.valueOf(caloriesSum / daysWithFood));
                proteinsCol.append(String.valueOf(proteinsSum / daysWithFood));
                fatsCol.append(String.valueOf(fatsSum / daysWithFood));
                carbsCol.append(String.valueOf(carbsSum / daysWithFood));

                // Расчет среднего веса за неделю
                String avgWeight = weekWeights.isEmpty() ? "—" :
                        weekWeights.stream()
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(BigDecimal.valueOf(weekWeights.size()), 1, RoundingMode.HALF_UP)
                                .stripTrailingZeros().toPlainString();
                weightCol.append(avgWeight);
            }
            weekStart = weekStart.plusWeeks(1);
        }
        return Table.create("Отчет по неделям", weekCol, caloriesCol, proteinsCol, fatsCol, carbsCol, weightCol);
    }

    /**
     * Формирует детальную ежедневную таблицу.
     * Сопоставляет факт съеденного с целями (нормами), актуальными на конкретную дату.
     */
    public static Table buildPeriodReportPerDay(List<Dish> dishes, List<UserNutritionProfileEntry> targets, List<WeightEntry> weightEntries, UserSettings userSettings, Instant from, Instant to) {
        ZoneId zoneId = userSettings.getZoneId();

        // Используем NavigableMap для эффективного поиска "последнего известного веса" на дату
        NavigableMap<LocalDate, BigDecimal> weightByDate = new TreeMap<>();
        for (WeightEntry w : weightEntries) weightByDate.put(w.getEntryDate(), w.getWeightKg());

        StringColumn dateCol = StringColumn.create("Дата");
        StringColumn weightCol = StringColumn.create("Вес");
        StringColumn caloriesCol = StringColumn.create("Калории");
        StringColumn diffCol = StringColumn.create("Δ"); // Разница между фактом и целью
        StringColumn proteinsCol = StringColumn.create("Белки");
        StringColumn fatsCol = StringColumn.create("Жиры");
        StringColumn carbsCol = StringColumn.create("Углеводы");

        Map<LocalDate, List<Dish>> dishesByDate = dishes.stream()
                .collect(Collectors.groupingBy(d -> d.getCreated().atZone(zoneId).toLocalDate()));

        LocalDate startDate = from.atZone(zoneId).toLocalDate();
        LocalDate endDate = to.atZone(zoneId).toLocalDate();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<Dish> dayDishes = dishesByDate.getOrDefault(date, List.of());
            if (dayDishes.isEmpty()) continue; // Пропускаем дни без активности

            int cF = 0, pF = 0, fF = 0, cbF = 0;
            for (Dish d : dayDishes) {
                cF += nvl(d.getCalories());
                pF += nvl(d.getProteins());
                fF += nvl(d.getFats());
                cbF += nvl(d.getCarbohydrates());
            }

            // Находим нормы КБЖУ, которые действовали в этот конкретный день
            UserNutritionProfileEntry target = findTargetForDate(targets, date.atStartOfDay(zoneId).toInstant());

            dateCol.append(date.format(dateFormatter));

            // floorEntry позволяет найти вес на текущую дату или самый свежий вес в прошлом
            var weightEntry = weightByDate.floorEntry(date);
            weightCol.append(weightEntry != null ? weightEntry.getValue().stripTrailingZeros().toPlainString() : "—");

            caloriesCol.append(formatFactTarget(cF, target != null ? target.getCaloriesNormPerDay() : null));
            diffCol.append(target != null ? String.format("%+d", cF - target.getCaloriesNormPerDay()) : "—");
            proteinsCol.append(formatFactTarget(pF, target != null ? target.getProteinsNormGramsPerDay() : null));
            fatsCol.append(formatFactTarget(fF, target != null ? target.getFatsNormGramsPerDay() : null));
            carbsCol.append(formatFactTarget(cbF, target != null ? target.getCarbsNormGramsPerDay() : null));
        }
        return Table.create("Дневной отчет", dateCol, weightCol, caloriesCol, diffCol, proteinsCol, fatsCol, carbsCol);
    }

    /**
     * Конвертирует структуру Table в HTML-строку для вставки в PDF шаблон.
     */
    public static String tableToHtml(Table table) {
        StringBuilder sb = new StringBuilder("<table><tr>");
        // Заголовки
        table.columnNames().forEach(c -> sb.append("<th>").append(c).append("</th>"));
        sb.append("</tr>");

        // Строки данных
        for (int r = 0; r < table.rowCount(); r++) {
            sb.append("<tr>");
            for (int c = 0; c < table.columnCount(); c++) {
                sb.append("<td>").append(escapeHtml(String.valueOf(table.get(r, c)))).append("</td>");
            }
            sb.append("</tr>");
        }
        return sb.append("</table>").toString();
    }

    /**
     * Экранирует HTML-символы во избежание поломки PDF-рендерера при наличии спецсимволов в названиях еды.
     */
    public static String escapeHtml(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Null-safe превращение Integer в примитив.
     */
    public static int nvl(Integer v) { return v != null ? v : 0; }

    /**
     * Форматирует строку вида "Факт / Цель" (например, "1800 / 2000").
     */
    public static String formatFactTarget(int fact, Integer target) {
        return target != null ? fact + " / " + target : String.valueOf(fact);
    }

    /**
     * Ищет запись профиля питания (нормы), которая была валидна в указанный момент времени.
     */
    public static UserNutritionProfileEntry findTargetForDate(List<UserNutritionProfileEntry> entries, Instant instant) {
        for (UserNutritionProfileEntry e : entries) {
            boolean afterFrom = !instant.isBefore(e.getValidFrom());
            boolean beforeTo = e.getValidTo() == null || instant.isBefore(e.getValidTo());
            if (afterFrom && beforeTo) return e;
        }
        return null;
    }

    public static Table buildDetailedDishTable(List<Dish> dishes, List<UserNutritionProfileEntry> targets, ZoneId zoneId) {
        // Создаем колонки
        StringColumn dateCol = StringColumn.create("Дата");
        StringColumn nameCol = StringColumn.create("Блюдо");
        StringColumn calCol = StringColumn.create("Калории");
        StringColumn targetCol = StringColumn.create("Цель калорий");
        StringColumn pCol = StringColumn.create("Белки");
        StringColumn fCol = StringColumn.create("Жиры");
        StringColumn cCol = StringColumn.create("Углеводы");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yy");

        // Сортируем блюда по дате
        dishes.sort(Comparator.comparing(Dish::getCreated));

        for (Dish dish : dishes) {
            LocalDate date = dish.getCreated().atZone(zoneId).toLocalDate();
            UserNutritionProfileEntry target = findTargetForDate(targets, dish.getCreated());

            dateCol.append(date.format(dtf));
            nameCol.append(dish.getName());
            calCol.append(String.valueOf(nvl(dish.getCalories())));
            targetCol.append(target != null ? String.valueOf(target.getCaloriesNormPerDay()) : "—");
            pCol.append(String.valueOf(nvl(dish.getProteins())));
            fCol.append(String.valueOf(nvl(dish.getFats())));
            cCol.append(String.valueOf(nvl(dish.getCarbohydrates())));
        }

        return Table.create("Детальный отчет", dateCol, nameCol, calCol, targetCol, pCol, fCol, cCol);
    }

    // Добавьте этот метод в класс ReportUtils
    public static Table buildWeightHistoryTable(List<WeightEntry> weightEntries, Instant from, Instant to, ZoneId zoneId) {
        LocalDate startDate = from.atZone(zoneId).toLocalDate();
        LocalDate endDate = to.atZone(zoneId).toLocalDate();

        StringColumn dateCol = StringColumn.create("Дата");
        StringColumn weightCol = StringColumn.create("Вес (кг)");
        StringColumn deltaCol = StringColumn.create("Изменение");

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // Сортируем по дате, чтобы правильно считать дельту
        List<WeightEntry> sortedEntries = weightEntries.stream()
                .filter(w -> !w.getEntryDate().isBefore(startDate) && !w.getEntryDate().isAfter(endDate))
                .sorted(Comparator.comparing(WeightEntry::getEntryDate))
                .toList();

        BigDecimal previousWeight = null;

        for (WeightEntry entry : sortedEntries) {
            dateCol.append(entry.getEntryDate().format(dateFormatter));
            weightCol.append(entry.getWeightKg().stripTrailingZeros().toPlainString());

            if (previousWeight != null) {
                BigDecimal delta = entry.getWeightKg().subtract(previousWeight);
                deltaCol.append(String.format("%+.1f", delta));
            } else {
                deltaCol.append("—");
            }
            previousWeight = entry.getWeightKg();
        }

        return Table.create("Таблица веса", dateCol, weightCol, deltaCol);
    }

    // Добавьте это в ReportUtils.java

    /**
     * Генерирует HTML-визуализацию распределения калорий по категориям.
     * Использует простые HTML-элементы для совместимости с PDF-рендерером.
     */
    /**
     * Генерирует горизонтальный чарт распределения калорий по категориям (в %).
     */
    public static String buildCategoryBarChartHtml(List<Dish> dishes) {
        if (dishes == null || dishes.isEmpty()) return "";

        Map<String, Integer> catMap = new HashMap<>();
        int totalCal = 0;
        for (Dish d : dishes) {
            String catName = (d.getCategory() != null) ? d.getCategory().getName() : "Прочее";
            int cal = nvl(d.getCalories());
            catMap.put(catName, catMap.getOrDefault(catName, 0) + cal);
            totalCal += cal;
        }
        if (totalCal == 0) return "";

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(catMap.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='margin-top: 20px; font-family: \"DejaVu Sans\", sans-serif;'>");
        sb.append("<h3 style='border-bottom: 1px solid #eee; padding-bottom: 5px; font-size: 14px;'>Распределение по категориям (%)</h3>");

        String[] colors = {"#4e73df", "#1cc88a", "#36b9cc", "#f6c23e", "#e74a3b", "#858796"};
        int colorIdx = 0;

        for (Map.Entry<String, Integer> entry : sorted) {
            double percent = (entry.getValue() * 100.0) / totalCal;
            String color = colors[colorIdx % colors.length];
            colorIdx++;

            sb.append("<div style='margin-bottom: 8px;'>");
            sb.append(String.format("<div style='font-size: 10px; margin-bottom: 2px;'>%s <span style='color: #888;'>— %.1f%%</span></div>",
                    escapeHtml(entry.getKey()), percent));
            sb.append("<div style='background-color: #eaecf4; border-radius: 3px; width: 100%; height: 10px;'>");
            sb.append(String.format("<div style='background-color: %s; width: %.1f%%; height: 10px; border-radius: 3px;'></div>", color, percent));
            sb.append("</div></div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Генерирует горизонтальный чарт распределения калорий по часам (Ритм питания).
     * Теперь без таблиц, в едином стиле с категориями.
     */
    public static String buildHourlyCaloriesChartHtml(List<Dish> dishes, ZoneId zoneId) {
        if (dishes == null || dishes.isEmpty()) return "";

        long[] hourlyCals = new long[24];
        long totalCal = 0;
        for (Dish d : dishes) {
            int hour = d.getCreated().atZone(zoneId).getHour();
            int cal = nvl(d.getCalories());
            hourlyCals[hour] += cal;
            totalCal += cal;
        }
        if (totalCal == 0) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='margin-top: 25px; font-family: \"DejaVu Sans\", sans-serif;'>");
        sb.append("<h3 style='border-bottom: 1px solid #eee; padding-bottom: 5px; font-size: 14px;'>Ритм питания по часам (%)</h3>");

        // Контейнер с вертикальными линиями сетки на фоне
        sb.append("<div style='position: relative; padding: 10px 0; border-left: 2px solid #5a5c69;'>");

        // Отрисовка вертикальных линий сетки (каждые 25%)
        sb.append("<div style='position: absolute; left: 25%; top: 0; bottom: 0; border-left: 1px dashed #e1e1e1;'></div>");
        sb.append("<div style='position: absolute; left: 50%; top: 0; bottom: 0; border-left: 1px dashed #e1e1e1;'></div>");
        sb.append("<div style='position: absolute; left: 75%; top: 0; bottom: 0; border-left: 1px dashed #e1e1e1;'></div>");

        for (int h = 0; h < 24; h++) {
            double percent = (hourlyCals[h] * 100.0) / totalCal;
            if (percent < 0.1) continue; // Пропускаем пустые часы для компактности

            // Ночные часы (22-06) выделим красным, остальные синим
            String color = (h >= 22 || h < 6) ? "#e74a3b" : "#4e73df";

            sb.append("<div style='margin-bottom: 5px; position: relative; z-index: 2;'>");
            sb.append(String.format("<div style='font-size: 9px; margin-bottom: 1px;'>%02d:00 <span style='color: #888;'>— %.1f%%</span></div>", h, percent));
            sb.append("<div style='background-color: #eaecf4; border-radius: 2px; width: 100%; height: 8px;'>");
            sb.append(String.format("<div style='background-color: %s; width: %.1f%%; height: 8px; border-radius: 2px;'></div>", color, percent));
            sb.append("</div></div>");
        }

        sb.append("</div>"); // Конец контейнера с сеткой
        sb.append("<div style='display: flex; justify-content: space-between; font-size: 8px; color: #aaa; margin-top: 2px; padding-left: 2px;'>");
        sb.append("<span>0%</span><span>25%</span><span>50%</span><span>75%</span><span>100%</span>");
        sb.append("</div></div>");

        return sb.toString();
    }
}