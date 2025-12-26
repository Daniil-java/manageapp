package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfileEntry;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportTableService {
    private final DishService dishService;
    private final UserNutritionProfileService userNutritionProfileService;
    private final UserNutritionProfileEntryService userNutritionProfileEntryService;
    private final UserSettingsService userSettingsService;
    private final WeightEntryService weightEntryService;
    private final WaterEntryService waterEntryService;
    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final TelegramCaloriesBotKeyComponents components;
    // таблица %s
    private static final String AI_REQUEST_PREDICTION =
            """
        Ты – профессиональный диетолог и специалист по снижению веса. 
        Отвечай кратко, по делу, без приветствий, без вводных фраз, без выводов и обобщающих «итогов». 
        Не используй маркированные списки, эмодзи и лишний текст. 
        Пиши только по заданной структуре и не добавляй ничего сверх неё.

        Входные данные: таблица с историей веса и питания пользователя.
        Формат таблицы:
        Дата | Вес (кг) | Калории (ккал) | Δ (изменение веса за день, кг) | Белки (г) | Жиры (г) | Углеводы (г)

        Требуется один текстовый ответ строго по следующей структуре:

        1) Прогноз веса:
        Прогноз веса на 7 дней: ВЕС_ЧЕРЕЗ_7_ДНЕЙ кг. Уверенность: проценты.
        Прогноз веса на 30 дней: ВЕС_ЧЕРЕЗ_30_ДНЕЙ кг. Уверенность: проценты.
        Прогноз веса на 6 месяцев: ВЕС_ЧЕРЕЗ_6_МЕСЯЦЕВ кг. Уверенность: проценты.
        Почему такой прогноз: ПРИЧИНА_ПРОГНОЗА.

        Требования к прогнозам:
        - Опираться на динамику веса и калорий, а не угадывать случайно.
        - Если данных мало или они нестабильны, указывай низкую уверенность (например, <50проценты) и делай прогноз более консервативным.
        - Вес указывать с точностью до 0.1 кг.
        - Ты не должен заниматься простой интерполяцией. Ты должен сделать прогноз, как профессионал в этой области.
        - Уверенность указывать целым числом процентов (0–100).

        Требования к причине прогноза:
        - Ты должен указать, почему ты вывел такой прогноз. Возможно пользователь есть слишком много углеводов или часто выходит за рамки

        2) Анализ питания:
        Анализ питания: КРАТКИЙ_ТЕКСТ_АНАЛИЗА

        Требования к анализу:
        - Проанализируй средний уровень калорий и соотношение Б/Ж/У.
        - Укажи возможные недостатки питания (недобор/перебор калорий, белков, жиров, углеводов), если они есть.
        - Укажи не только проблему, но и сдержанную, профессиональную рекомендацию, как это скорректировать.
        - Пиши нейтрально и уважительно, избегай оценочных суждений о пользователе (никаких «плохо питаешься», «неправильно делаешь» и т.п.).
        - Не давай конкретных медицинских диагнозов и не заменяй консультацию врача. При сомнениях укажи, что для точных медицинских рекомендаций нужен врач или личный диетолог.

        ВАЖНО:
        - Учитывай, что пользователь может не только хотет похудеть, но и набрать, и сохранить свой вес
        - Не добавляй комментарии о том, как ты делал расчёты.
        - Не повторяй и не пересказывай таблицу.
        - Не давай общую теорию питания, только конкретные выводы по предоставленным данным.
        - Если данных слишком мало для надёжного прогноза, прямо укажи это в тексте прогноза и снизь уверенность.

        Таблица: %s
        
        """;

    public ReportResponse getPeriodReport(Instant from, Instant to, Long userId) {
        List<Dish> dishes =
                dishService.getAllDishedByUserIdAndPeriod(userId, from, to);

        List<UserNutritionProfileEntry> targets =
                userNutritionProfileEntryService.getAllByUserId(userId);

        List<WeightEntry> weightEntries =
                weightEntryService.getAllWeightHistory(userId);

        UserSettings userSettings = userSettingsService.getOrCreate(userId);

        Table periodTable = buildPeriodReport(dishes, targets, weightEntries, userSettings, from, to);

        String aiRequest = String.format(AI_REQUEST_PREDICTION, periodTable.print());
        String response = openAiProviderProcessor.fetchResponse(
                components.getAiKey(),
                aiRequest,
                BotIdentifier.CALORIE_BOT,
                "PERIOD REPORT"
        );

        System.out.println(response);
        return new ReportResponse(periodTable, response, aiRequest);
    }

    public record ReportResponse (Table table, String text, String aiRequest) {}

    //Дата  -   Ккал (Факт / Цель)  -   Статус(превысил/или недоел)  -   Белки   -   Жиры    -   Углеводы
    //Одна строка - один день
    public static Table buildPeriodReport(
            List<Dish> dishes,
            List<UserNutritionProfileEntry> targets,
            List<WeightEntry> weightEntries,
            UserSettings userSettings,
            Instant from, Instant to
            ) {
        ZoneId zoneId = userSettings.getZoneId();

        // --- вес: последний известный на дату ---
        NavigableMap<LocalDate, BigDecimal> weightByDate = new TreeMap<>();
        for (WeightEntry w : weightEntries) {
            weightByDate.put(w.getEntryDate(), w.getWeightKg());
        }

        // --- колонки таблицы ---
        StringColumn dateCol = StringColumn.create("Дата");
        StringColumn weightCol = StringColumn.create("Вес");
        StringColumn caloriesCol = StringColumn.create("Калории");
        StringColumn diffCol = StringColumn.create("Δ");
        StringColumn proteinsCol = StringColumn.create("Белки");
        StringColumn fatsCol = StringColumn.create("Жиры");
        StringColumn carbsCol = StringColumn.create("Углеводы");

        // --- группируем блюда по локальной дате ---
        Map<LocalDate, List<Dish>> dishesByDate = dishes.stream()
                .collect(Collectors.groupingBy(d ->
                        d.getCreated()
                                .atZone(zoneId)
                                .toLocalDate()
                ));

        LocalDate startDate = from.atZone(zoneId).toLocalDate();
        LocalDate endDate = to.atZone(zoneId).toLocalDate();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {

            List<Dish> dayDishes = dishesByDate.getOrDefault(date, List.of());

            // пропускаем дни без еды
            if (dayDishes.isEmpty()) {
                continue;
            }

            int caloriesFact = 0;
            int proteinsFact = 0;
            int fatsFact = 0;
            int carbsFact = 0;

            for (Dish d : dayDishes) {
                caloriesFact += nvl(d.getCalories());
                proteinsFact += nvl(d.getProteins());
                fatsFact += nvl(d.getFats());
                carbsFact += nvl(d.getCarbohydrates());
            }

            // --- цель, актуальная на этот день ---
            Instant dayStart = date.atStartOfDay(zoneId).toInstant();
            UserNutritionProfileEntry target = findTargetForDate(targets, dayStart);

            Integer caloriesTarget = target != null ? target.getCaloriesNormPerDay() : null;
            Integer proteinsTarget = target != null ? target.getProteinsNormGramsPerDay() : null;
            Integer fatsTarget = target != null ? target.getFatsNormGramsPerDay() : null;
            Integer carbsTarget = target != null ? target.getCarbsNormGramsPerDay() : null;

            String caloriesText = formatFactTarget(caloriesFact, caloriesTarget);
            String proteinsText = formatFactTarget(proteinsFact, proteinsTarget);
            String fatsText = formatFactTarget(fatsFact, fatsTarget);
            String carbsText = formatFactTarget(carbsFact, carbsTarget);

            String diffText = caloriesTarget != null
                    ? String.format("%+d", caloriesFact - caloriesTarget)
                    : "—";

            // --- вес: последний известный ≤ дате ---
            Map.Entry<LocalDate, BigDecimal> weightEntry = weightByDate.floorEntry(date);
            BigDecimal weight = weightEntry != null ? weightEntry.getValue() : null;

            String weightText = weight != null
                    ? weight.stripTrailingZeros().toPlainString()
                    : "—";

            // --- добавляем строку ---
            dateCol.append(date.format(dateFormatter));
            weightCol.append(weightText);
            caloriesCol.append(caloriesText);
            diffCol.append(diffText);
            proteinsCol.append(proteinsText);
            fatsCol.append(fatsText);
            carbsCol.append(carbsText);
        }

        Table table = Table.create("Отчет по питанию",
                dateCol,
                weightCol,
                caloriesCol,
                diffCol,
                proteinsCol,
                fatsCol,
                carbsCol
        );

        log.info("\n{}", table.print());
        return table;
    }

    private static int nvl(Integer v) {
        return v != null ? v : 0;
    }

    private static String formatFactTarget(int fact, Integer target) {
        return target != null
                ? fact + " / " + target
                : String.valueOf(fact);
    }

    private static UserNutritionProfileEntry findTargetForDate(
            List<UserNutritionProfileEntry> entries,
            Instant instant
    ) {
        for (UserNutritionProfileEntry e : entries) {
            boolean afterFrom = !instant.isBefore(e.getValidFrom());
            boolean beforeTo = e.getValidTo() == null || instant.isBefore(e.getValidTo());
            if (afterFrom && beforeTo) {
                return e;
            }
        }
        return null;
    }

}
