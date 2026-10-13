package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Недельный отчёт: русский — как раньше, английский — с «day / days» по числу. */
class WeekTextsTest {

    private static final Long RU_USER = 1L;
    private static final Long EN_USER = 2L;

    private WeekTexts weekTexts;

    private final UserNutritionProfile profile = new UserNutritionProfile()
            .setCaloriesNormPerDay(2000).setProteinsNormGramsPerDay(100);
    private final List<Dish> dishes = List.of(
            new Dish().setName("Omelette").setCalories(1800).setProteins(100).setFats(60).setCarbohydrates(150)
                    .setCreated(Instant.parse("2026-10-10T09:00:00Z")),
            new Dish().setName("Pizza").setCalories(2500).setProteins(90).setFats(100).setCarbohydrates(280)
                    .setCreated(Instant.parse("2026-10-11T19:00:00Z")));

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(RU_USER)).thenReturn("ru");
        when(settings.findLanguageCode(EN_USER)).thenReturn("en");
        when(settings.getOrCreate(any())).thenReturn(new UserSettings().setTimezoneId("UTC"));
        weekTexts = new WeekTexts(new CalorieTexts(new I18nProperties(), settings), settings);
    }

    @Test
    void russianWeekReport() {
        String text = weekTexts.buildWeekReportText(dishes, profile, RU_USER);

        assertThat(text).startsWith("📊 <b>ЕЖЕНЕДЕЛЬНЫЙ ОТЧЕТ ПИТАНИЯ</b>\n");
        assertThat(text).contains(
                "  └ 1800 ккал | Б:100 Ж:60 У:150",
                "<b>🔴 Итого за день:</b>\n🔥 2500 / 2000 ккал ⚠️ (+500)",
                "✅ Дисциплина (калории): <b>1 из 2 дн.</b>",
                "🧮 Среднее потребление: <b>2150 ккал/день</b>",
                "📝 <b>Вердикт:</b> Рацион сбалансирован, вы отлично справляетесь! 🎯");
    }

    @Test
    void englishWeekReportDeclinesDays() {
        String text = weekTexts.buildWeekReportText(dishes, profile, EN_USER);

        assertThat(text).contains("✅ Calorie discipline: <b>1 of 2 days</b>", "🔥 2500 / 2000 kcal");
        assertThat(weekTexts.buildWeekReportText(dishes.subList(0, 1), profile, EN_USER))
                .contains("<b>1 of 1 day</b>");
        assertThat(text).doesNotContainPattern("[А-Яа-яЁё]");
    }
}
