package com.kuklin.manageapp.bots.caloriebot.i18n;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfileEntry;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.models.airesponse.AiPatternAnalysisResponse;
import com.kuklin.manageapp.bots.caloriebot.models.airesponse.MacroBalanceDTO;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * PDF-отчёт на английском: в шаблонах, таблицах, диаграммах и разборе ИИ не остаётся русского текста
 * и неподставленных {{pdf.…}}.
 */
class PdfTextsTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Instant FROM = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-11T23:59:59Z");

    private final CalorieTexts texts = new CalorieTexts(new I18nProperties(), mock(UserSettingsService.class));
    private final PdfTexts en = new PdfTexts(texts, AppLanguage.EN);
    private final PdfTexts ru = new PdfTexts(texts, AppLanguage.RU);

    @Test
    void templatesInEnglishHaveNoRussianAndNoKeys() throws IOException {
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
    void templatesInRussianKeepRussianHeadings() throws IOException {
        String html = ru.localizeTemplate(TextBundlesConsistencyTest.read("templates/weeklyreport.html"));

        assertThat(html)
                .contains("<h1>Недельный отчёт</h1>")
                .contains("content: \"Страница \" counter(page) \" из \" counter(pages);");
    }

    @Test
    void tablesAndChartsInEnglish() {
        List<Dish> dishes = List.of(
                dish("2026-10-06T08:00:00Z", Dish.FoodCategory.BREAKFAST_ITEM),
                dish("2026-10-07T13:00:00Z", Dish.FoodCategory.SOUP),
                dish("2026-10-08T19:00:00Z", null));
        List<UserNutritionProfileEntry> targets = List.of();
        List<WeightEntry> weights = List.of(new WeightEntry().setEntryDate(LocalDate.of(2026, 10, 6)).setWeight(new BigDecimal("70.5")));
        UserSettings settings = new UserSettings().setTimezoneId(ZONE.getId());

        String html = ReportUtils.tableToHtml(ReportUtils.buildDetailedDishTable(dishes, targets, ZONE, en))
                + ReportUtils.tableToHtml(ReportUtils.buildPeriodReportPerDay(dishes, targets, weights, settings, FROM, TO, en))
                + ReportUtils.tableToHtml(ReportUtils.buildPeriodReportPerWeek(dishes, weights, settings, FROM, TO, en))
                + ReportUtils.tableToHtml(ReportUtils.buildWeightHistoryTable(weights, FROM, TO, ZONE, en))
                + ReportUtils.buildCategoryBarChartHtml(dishes, en)
                + ReportUtils.buildHourlyCaloriesChartHtml(dishes, ZONE, en);

        assertThat(html)
                .doesNotContainPattern("\\p{IsCyrillic}")
                .contains("<th>Date</th>", "<th>Avg kcal</th>", "<th>Weight (kg)</th>")
                .contains("Breakfast", "Soup", "Other")
                .contains("Oct 6, 2026", "Oct 5 – Oct 11");
    }

    @Test
    void aiAnalysisHeadingsInUserLanguageAndAiTextEscaped() {
        var analysis = new AiPatternAnalysisResponse();
        analysis.setMacroBalance(new MacroBalanceDTO());
        analysis.getMacroBalance().setImbalanceAnalysis("Protein < 20%");
        analysis.setRecommendations(List.of("Eat <b>more</b> vegetables"));

        String html = analysis.toHtml(en);

        assertThat(html)
                .doesNotContainPattern("\\p{IsCyrillic}")
                .contains("Macronutrient balance", "Protein", "Recommendations")
                .contains("Protein &lt; 20%")
                .contains("Eat &lt;b&gt;more&lt;/b&gt; vegetables");
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
