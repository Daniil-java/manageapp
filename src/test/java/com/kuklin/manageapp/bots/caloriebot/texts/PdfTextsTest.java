package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.i18n.TextBundlesConsistencyTest;
import com.kuklin.manageapp.bots.caloriebot.models.airesponse.ReportAiResponse;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.WaterEntryDto;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportKind;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportSource;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportHtml;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportStatsCalculator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * PDF-отчёт на английском: в шаблоне и блоках не остаётся русского текста и неподставленных {{pdf.…}};
 * числа и даты — в формате языка пользователя, текст ИИ экранируется.
 */
class PdfTextsTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDate START = LocalDate.of(2026, 10, 5);

    private final CalorieTexts texts = new CalorieTexts(new I18nProperties(), mock(UserSettingsService.class));
    private final PdfTexts en = new PdfTexts(texts, AppLanguage.EN);
    private final PdfTexts ru = new PdfTexts(texts, AppLanguage.RU);

    @Test
    void templateInEnglishHasNoRussianAndNoKeys() throws IOException {
        for (String template : TextBundlesConsistencyTest.PDF_TEMPLATES) {
            String html = en.localizeTemplate(TextBundlesConsistencyTest.read(template));

            assertThat(withoutComments(html)).as(template).doesNotContainPattern("\\p{IsCyrillic}");
            assertThat(html).as(template)
                    .doesNotContain("{{pdf.")
                    .doesNotContain("{{LANG}}")
                    .contains("<html lang=\"en\"");
        }
    }

    @Test
    void templateInRussianHasRussianFooter() throws IOException {
        String html = ru.localizeTemplate(TextBundlesConsistencyTest.read("templates/report.html"));

        assertThat(html).contains("content: \"стр. \" counter(page) \" из \" counter(pages);");
    }

    @Test
    void numbersAndDatesInUserLanguage() {
        assertThat(ru.formatInt(1850)).isEqualTo("1 850");
        assertThat(en.formatInt(1850)).isEqualTo("1,850");
        assertThat(ru.formatSigned(-1.42, 1)).isEqualTo("−1,4");
        assertThat(en.formatSigned(0.36, 1)).isEqualTo("+0.4");
        assertThat(en.formatSigned(-0.01, 1)).isEqualTo("0.0");
        assertThat(ru.formatDate(START, "pdf.date-long")).isEqualTo("5 октября");
        assertThat(en.formatDate(START, "pdf.date-day")).isEqualTo("Oct 5");
        assertThat(ru.formatWeekday(START.getDayOfWeek())).isEqualTo("Пн");
        assertThat(en.formatMinutes(9 * 60 + 5)).isEqualTo("09:05");
    }

    @Test
    void reportBlocksInEnglish() {
        ReportStats stats = ReportStatsCalculator.buildStats(new ReportSource(
                ReportKind.WEEK, START, START.plusDays(6), ZONE,
                List.of(dish("2026-10-06T08:00:00Z", Dish.FoodCategory.BREAKFAST_ITEM),
                        dish("2026-10-07T13:00:00Z", Dish.FoodCategory.SOUP),
                        dish("2026-10-11T22:00:00Z", null)),
                List.of(), List.of(),
                List.of(weight(START, "70.5"), weight(START.plusDays(5), "70.1")),
                List.of(new WaterEntryDto().setEntryDate(START).setAmount(1500)),
                new UserNutritionProfile().setGoal(UserNutritionProfile.Goal.LOSE_WEIGHT).setCaloriesNormPerDay(2000)
                        .setActivityLevel(UserNutritionProfile.ActivityLevel.MEDIUM)));
        ReportAiResponse ai = new ReportAiResponse("Good week",
                List.of(new ReportAiResponse.Pattern("🥣", "Breakfasts", "Eat <b>more</b> & better", "↑ fine", "positive")),
                List.of(new ReportAiResponse.Swap("🍫 Chocolate", "🍎 Apple", "−130 kcal/day")));

        String html = ReportHtml.buildCover(stats, ai, new UserNutritionProfile().setGoal(UserNutritionProfile.Goal.LOSE_WEIGHT), en, "<svg/>",
                ZonedDateTime.of(2026, 10, 11, 21, 0, 0, 0, ZONE))
                + ReportHtml.buildCaloriesSection(stats, en)
                + ReportHtml.buildWeightSection(stats, en)
                + ReportHtml.buildMacrosSection(stats, en)
                + ReportHtml.buildRhythmSection(stats, en)
                + ReportHtml.buildTopSection(stats, en)
                + ReportHtml.buildCategoriesSection(stats, en)
                + ReportHtml.buildPatternsSection(ai, en)
                + ReportHtml.buildSwapsSection(ai, en)
                + ReportHtml.buildWaterSection(stats, en)
                + ReportHtml.buildDailySection(stats, en)
                + ReportHtml.buildQualitySection(stats, en);

        assertThat(html)
                .doesNotContainPattern("\\p{IsCyrillic}")
                .doesNotContain("{{")
                .contains("Weekly report", "Calories vs target", "Breakfast", "Soup", "Other", "Day by day")
                .contains("Eat &lt;b&gt;more&lt;/b&gt; &amp; better");
    }

    @Test
    void categoryWithoutValueIsOther() {
        assertThat(en.category(null)).isEqualTo("Other");
        assertThat(ru.category(Dish.FoodCategory.MAIN_COURSE)).isEqualTo("Основное блюдо");
    }

    /** CSS- и HTML-комментарии в шаблонах — для разработчика, в PDF не попадают. */
    private static String withoutComments(String html) {
        return html.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?s)<!--.*?-->", "");
    }

    private static WeightEntry weight(LocalDate date, String kg) {
        return new WeightEntry().setEntryDate(date).setWeight(new BigDecimal(kg));
    }

    private static Dish dish(String createdUtc, Dish.FoodCategory category) {
        return new Dish()
                .setName("Oatmeal")
                .setCategory(category)
                .setCalories(350)
                .setProteins(12)
                .setFats(8)
                .setCarbohydrates(55)
                .setCreated(Instant.parse(createdUtc));
    }
}
