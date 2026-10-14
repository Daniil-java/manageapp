package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.configurations.AiInputLimitsProperties;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.*;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.WaterEntryDto;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportKind;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PDF-отчёты недели и месяца: собираются на реалистичных данных, ИИ вызывается один раз в JSON-режиме
 * с готовыми цифрами, без еды и при ошибке ИИ отчёт всё равно рендерится.
 * <p>
 * Посмотреть отчёты глазами: {@code -Dreport.preview.dir=<папка>} — туда лягут PDF и PNG каждой страницы.
 */
class ReportServicePdfTest {

    private static final Long USER_ID = 42L;
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Instant NOW = Instant.parse("2026-10-13T18:00:00Z");
    private static final LocalDate START = LocalDate.of(2026, 9, 14);
    private static final Instant TARGET_CHANGED = LocalDate.of(2026, 10, 1).atStartOfDay(ZONE).toInstant();

    private final DishService dishService = mock(DishService.class);
    private final WaterEntryService waterService = mock(WaterEntryService.class);
    private final OpenAiProviderProcessor ai = mock(OpenAiProviderProcessor.class);
    private final UserSettingsService settingsService = mock(UserSettingsService.class);

    @Test
    void reportsAskAiOnceInJsonModeWithCalculatedNumbers() {
        ReportService service = buildService("en", false);
        givenAiAnswers(AI_ANSWER_EN);

        byte[] week = service.buildPdfReportOrNull(ReportKind.WEEK, NOW, USER_ID);
        byte[] month = service.buildPdfReportOrNull(ReportKind.MONTH, NOW, USER_ID);

        assertThat(week).isNotEmpty();
        assertThat(month).isNotEmpty();
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai, times(2)).fetchJsonResponse(anyString(), prompt.capture(), any(), anyString(), any());
        verify(ai, never()).fetchResponse(anyString(), anyString(), any(), anyString(), any());
        assertThat(prompt.getAllValues().get(0)).contains("за 7 дней");
        assertThat(prompt.getAllValues().get(1)).contains("за 30 дней");
        assertThat(prompt.getAllValues()).allSatisfy(p -> assertThat(p)
                .contains("ЯЗЫК ОТВЕТА: English.")
                .contains("\"avgCalories\":")
                .contains("\"realExpenditureKcal\":")
                .contains("\"topByCalories\":")
                // сырые блюда и служебные поля в ИИ не уходят
                .doesNotContain("\"userId\"")
                .doesNotContain("aiConfidence"));
    }

    @Test
    void englishMonthReportHasAllSectionsAndNoRussian() throws IOException {
        ReportService service = buildService("en", false);
        givenAiAnswers(AI_ANSWER_EN);

        String text = extractText(service.buildPdfReportOrNull(ReportKind.MONTH, NOW, USER_ID));

        assertThat(text)
                .contains("Monthly report", "Month calendar", "Calories vs target", "Weight", "REAL ENERGY EXPENDITURE",
                        "Macro balance", "Eating rhythm", "Behaviour patterns", "Swaps for next week", "Water", "Day by day")
                .contains("Late-night snacks")
                .doesNotContain("{{")
                .doesNotContainPattern("\\p{IsCyrillic}");
    }

    @Test
    void weekReportListsDishesByDayWithoutCalendar() throws IOException {
        ReportService service = buildService("ru", true);
        givenAiAnswers(AI_ANSWER_RU);

        String text = extractText(service.buildPdfReportOrNull(ReportKind.WEEK, NOW, USER_ID));

        assertThat(text)
                .contains("Отчёт за неделю", "Блюда по дням", "Итого за день", "Овсянка с бананом")
                .doesNotContain("Календарь месяца")
                .doesNotContain("{{");
    }

    @Test
    void reportWithoutFoodIsRenderedWithoutAi() throws IOException {
        ReportService service = buildService("ru", true);
        when(dishService.getAllDishedByUserIdAndPeriod(eq(USER_ID), any(), any())).thenReturn(List.of());

        String text = extractText(service.buildPdfReportOrNull(ReportKind.MONTH, NOW, USER_ID));

        assertThat(text).contains("нет записей еды");
        verifyNoInteractions(ai);
    }

    @Test
    void aiFailureStillRendersReportWithoutAiBlocks() throws IOException {
        ReportService service = buildService("ru", true);
        when(ai.fetchJsonResponse(anyString(), anyString(), any(), anyString(), any())).thenThrow(new RuntimeException("Read timed out"));

        String text = extractText(service.buildPdfReportOrNull(ReportKind.MONTH, NOW, USER_ID));

        assertThat(text)
                .contains("Календарь месяца", "РЕАЛЬНЫЙ РАСХОД ЭНЕРГИИ")
                .doesNotContain("Паттерны поведения");
    }

    /** Не проверка, а превью: с -Dreport.preview.dir сохраняет все четыре отчёта в PDF и PNG. */
    @Test
    void savesPreviewWhenAsked() throws IOException {
        String dir = System.getProperty("report.preview.dir");
        if (dir == null) return;
        for (String language : List.of("ru", "en")) {
            reset(ai, dishService, waterService, settingsService);
            ReportService service = buildService(language, language.equals("ru"));
            givenAiAnswers(language.equals("ru") ? AI_ANSWER_RU : AI_ANSWER_EN);
            for (ReportKind kind : ReportKind.values()) {
                byte[] pdf = service.buildPdfReportOrNull(kind, NOW, USER_ID);
                String name = kind.name().toLowerCase() + "-" + language;
                Files.createDirectories(Path.of(dir));
                Files.write(Path.of(dir, name + ".pdf"), pdf);
                try (PDDocument doc = PDDocument.load(pdf)) {
                    PDFRenderer renderer = new PDFRenderer(doc);
                    for (int i = 0; i < doc.getNumberOfPages(); i++) {
                        ImageIO.write(renderer.renderImageWithDPI(i, Integer.getInteger("report.preview.dpi", 90)), "png", new File(dir, name + "-" + (i + 1) + ".png"));
                    }
                }
            }
        }
    }

    // ── Данные ──────────────────────────────────────────────────────

    private ReportService buildService(String language, boolean russianDishes) {
        UserNutritionProfileService profileService = mock(UserNutritionProfileService.class);
        when(profileService.getOrCreateProfile(USER_ID)).thenReturn(new UserNutritionProfile()
                .setUserId(USER_ID)
                .setSex(UserNutritionProfile.Sex.MALE)
                .setAgeYears(33)
                .setHeightCm(180)
                .setCurrentWeightKg(new BigDecimal("82.6"))
                .setActivityLevel(UserNutritionProfile.ActivityLevel.LOW)
                .setGoal(UserNutritionProfile.Goal.LOSE_WEIGHT)
                .setCaloriesNormPerDay(1750)
                .setProteinsNormGramsPerDay(130)
                .setFatsNormGramsPerDay(62)
                .setCarbsNormGramsPerDay(168)
                .setWaterTargetMlPerDay(2000));

        UserNutritionProfileEntryService entryService = mock(UserNutritionProfileEntryService.class);
        when(entryService.getAllByUserId(USER_ID)).thenReturn(List.of(
                target(1850, Instant.parse("2026-08-01T00:00:00Z"), TARGET_CHANGED),
                target(1750, TARGET_CHANGED, null)));

        when(settingsService.getZoneId(USER_ID)).thenReturn(ZONE);
        when(settingsService.findLanguageCode(USER_ID)).thenReturn(language);

        List<Dish> dishes = buildDishes(russianDishes);
        when(dishService.getAllDishedByUserIdAndPeriod(eq(USER_ID), any(), any())).thenAnswer(inv -> {
            Instant from = inv.getArgument(1), to = inv.getArgument(2);
            return dishes.stream().filter(d -> !d.getCreated().isBefore(from) && !d.getCreated().isAfter(to)).toList();
        });
        when(waterService.getAllWaterEntryByPeriod(eq(USER_ID), any(), any())).thenReturn(buildWater());

        WeightEntryService weightService = mock(WeightEntryService.class);
        when(weightService.getAllWeightHistory(USER_ID)).thenReturn(buildWeights());

        TelegramCaloriesBotKeyComponents components = mock(TelegramCaloriesBotKeyComponents.class);
        when(components.getAiKey()).thenReturn("test-ai-key");

        ReportSourceLoader loader = new ReportSourceLoader(dishService, profileService, entryService, settingsService, weightService, waterService);
        return new ReportService(dishService, profileService, settingsService, weightService, loader,
                ai, components, new AiInputLimitsProperties(), new CalorieTexts(new I18nProperties(), settingsService));
    }

    /** Месяц с прошлым периодом: будни около нормы, выходные с перебором, три пропуска. */
    private static List<Dish> buildDishes(boolean russian) {
        Random random = new Random(7);
        String[][] meals = russian
                ? new String[][]{{"🥣", "Овсянка с бананом"}, {"🍗", "Куриная грудка с рисом"}, {"🍫", "Шоколадный батончик"},
                {"🥗", "Салат с тунцом"}, {"🍕", "Пицца пепперони"}, {"🧀", "Сырники со сметаной"}}
                : new String[][]{{"🥣", "Oatmeal with banana"}, {"🍗", "Chicken breast with rice"}, {"🍫", "Chocolate bar"},
                {"🥗", "Tuna salad"}, {"🍕", "Pepperoni pizza"}, {"🧀", "Cottage cheese pancakes"}};
        List<Dish> dishes = new ArrayList<>();
        for (LocalDate date = START.minusDays(30); !date.isAfter(LocalDate.of(2026, 10, 13)); date = date.plusDays(1)) {
            if (date.equals(LocalDate.of(2026, 9, 20)) || date.equals(LocalDate.of(2026, 10, 4)) || date.equals(LocalDate.of(2026, 10, 10))) continue;
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            int target = date.isBefore(LocalDate.of(2026, 10, 1)) ? 1850 : 1750;
            double factor = weekend ? 1.05 + random.nextDouble() * 0.27 : 0.83 + random.nextDouble() * 0.17;
            int total = (int) (target * factor);
            dishes.add(dish(meals[0], date, 8 + random.nextInt(3), random.nextInt(60), total * 24 / 100, Dish.FoodCategory.BREAKFAST_ITEM));
            dishes.add(dish(meals[weekend ? 4 : 1], date, 13 + random.nextInt(2), random.nextInt(60), total * 36 / 100,
                    weekend ? Dish.FoodCategory.FAST_FOOD : Dish.FoodCategory.MAIN_COURSE));
            dishes.add(dish(meals[3], date, 19 + random.nextInt(2), random.nextInt(50), total * 28 / 100, Dish.FoodCategory.SALAD));
            dishes.add(dish(meals[weekend ? 5 : 2], date, 22, random.nextInt(50), total * 12 / 100,
                    weekend ? Dish.FoodCategory.DESSERT : Dish.FoodCategory.SNACK));
        }
        return dishes;
    }

    private static Dish dish(String[] meal, LocalDate date, int hour, int minute, int calories, Dish.FoodCategory category) {
        return new Dish()
                .setUserId(USER_ID)
                .setEmojiIcon(meal[0])
                .setName(meal[1])
                .setCategory(category)
                .setCalories(calories)
                .setProteins(calories * 21 / 400)
                .setFats(calories * 34 / 900)
                .setCarbohydrates(calories * 45 / 400)
                .setAiConfidence(84)
                .setCreated(date.atTime(hour, minute).atZone(ZONE).toInstant());
    }

    private static List<WeightEntry> buildWeights() {
        Random random = new Random(3);
        List<WeightEntry> weights = new ArrayList<>();
        for (int i = -5; i < 30; i++) {
            if (i != 29 && random.nextDouble() > 0.6) continue;
            double weight = 84.2 - 1.6 * i / 29 + (random.nextDouble() - 0.5) * 0.6;
            weights.add(new WeightEntry().setEntryDate(START.plusDays(i)).setWeight(BigDecimal.valueOf(Math.round(weight * 10) / 10.0)));
        }
        return weights;
    }

    private static List<WaterEntryDto> buildWater() {
        Random random = new Random(5);
        List<WaterEntryDto> water = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            water.add(new WaterEntryDto().setEntryDate(START.plusDays(i)).setAmount(1150 + random.nextInt(17) * 60));
        }
        return water;
    }

    private static UserNutritionProfileEntry target(int calories, Instant from, Instant to) {
        return new UserNutritionProfileEntry()
                .setUserId(USER_ID)
                .setGoal(UserNutritionProfile.Goal.LOSE_WEIGHT)
                .setCaloriesNormPerDay(calories)
                .setProteinsNormGramsPerDay(130)
                .setFatsNormGramsPerDay(calories == 1850 ? 68 : 62)
                .setCarbsNormGramsPerDay(calories == 1850 ? 180 : 168)
                .setValidFrom(from)
                .setValidTo(to);
    }

    private void givenAiAnswers(String response) {
        when(ai.fetchJsonResponse(anyString(), anyString(), any(), anyString(), any())).thenReturn(response);
    }

    private static String extractText(byte[] pdf) throws IOException {
        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static final String AI_ANSWER_RU = """
            {"headline":"Стабильный дефицит в будни, но выходные съедают около трети недельного прогресса — вес всё равно идёт вниз.",
             "patterns":[
               {"emoji":"🥣","title":"Надёжные завтраки","description":"Почти каждый день начинается с овсянки до 10:30 — утро самая стабильная часть рациона.","impact":"↑ держит дефицит в будни","sentiment":"positive"},
               {"emoji":"🍕","title":"Эффект выходных","description":"Сб–Вс на 480 ккал больше будней: пицца и сырники почти всегда приходятся на выходные.","impact":"↓ съедает треть дефицита","sentiment":"negative"},
               {"emoji":"🌙","title":"Поздние перекусы","description":"12% калорий — после 22:00, в будни шоколадный батончик, в выходные сырники.","impact":"↓ ~150 ккал в день","sentiment":"negative"},
               {"emoji":"💧","title":"Вода проседает","description":"В среднем 1 650 мл при норме 2 000 — норма выполнена лишь в несколько дней.","impact":"→ лёгкая точка роста","sentiment":"neutral"}],
             "swaps":[
               {"from":"🍫 Шоколадный батончик вечером","to":"🍎 Яблоко и 20 г орехов","saving":"−130 ккал/день"},
               {"from":"🍕 Пицца пепперони в выходные","to":"🌯 Домашняя шаурма с курицей","saving":"−450 ккал за раз"},
               {"from":"🧀 Сырники со сметаной","to":"🥞 Сырники из духовки с йогуртом","saving":"−180 ккал за раз"}]}
            """;

    private static final String AI_ANSWER_EN = """
            {"headline":"Steady weekday deficit, but weekends eat about a third of your weekly progress — the trend still points down.",
             "patterns":[
               {"emoji":"🥣","title":"Reliable breakfasts","description":"Almost every day starts with oatmeal before 10:30 — mornings are the most stable part of your diet.","impact":"↑ keeps the weekday deficit","sentiment":"positive"},
               {"emoji":"🍕","title":"Weekend effect","description":"Sat–Sun bring 480 kcal more than weekdays: pizza and pancakes land almost only on weekends.","impact":"↓ eats a third of the deficit","sentiment":"negative"},
               {"emoji":"🌙","title":"Late-night snacks","description":"12% of calories come after 10 pm — a chocolate bar on weekdays, pancakes on weekends.","impact":"↓ ~150 kcal a day","sentiment":"negative"},
               {"emoji":"💧","title":"Water lags behind","description":"About 1,650 ml against a 2,000 ml target — met only on a few days.","impact":"→ an easy win","sentiment":"neutral"}],
             "swaps":[
               {"from":"🍫 Evening chocolate bar","to":"🍎 Apple and 20 g of nuts","saving":"−130 kcal/day"},
               {"from":"🍕 Weekend pepperoni pizza","to":"🌯 Homemade chicken wrap","saving":"−450 kcal per meal"},
               {"from":"🧀 Pancakes with sour cream","to":"🥞 Baked pancakes with yogurt","saving":"−180 kcal per meal"}]}
            """;
}
