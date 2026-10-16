package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Тексты про блюда (карточка блюда, дневник «Сегодня»): русские — как до перевода, английские — без русских слов. */
class DishTextsTest {

    private static final Long RU_USER = 1L;
    private static final Long EN_USER = 2L;

    private DishService dishService;
    private UserNutritionProfileService profileService;
    private DishTexts dishTexts;

    private final Dish earlier = new Dish().setCalories(300).setProteins(10).setFats(5).setCarbohydrates(30);
    private final Dish dish = new Dish().setName("Гречка").setCalories(200).setProteins(7).setFats(2)
            .setCarbohydrates(40).setPortionWeight(150).setPortions(1);
    private final UserNutritionProfile profile = new UserNutritionProfile().setCaloriesNormPerDay(2000)
            .setProteinsNormGramsPerDay(100).setFatsNormGramsPerDay(70).setCarbsNormGramsPerDay(250);

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(RU_USER)).thenReturn("ru");
        when(settings.findLanguageCode(EN_USER)).thenReturn("en");
        dishService = mock(DishService.class);
        profileService = mock(UserNutritionProfileService.class);
        when(dishService.getTodayDishes(any())).thenReturn(List.of(earlier, dish));
        when(profileService.getOrCreateProfile(any())).thenReturn(profile);

        dishTexts = new DishTexts(new CalorieTexts(new I18nProperties(), settings), dishService, profileService);
    }

    @Test
    void russianCardWithTargetsLooksAsBefore() {
        when(profileService.checkTargetCalculateParams(profile)).thenReturn(true);

        assertThat(dishTexts.buildDishCardText(dish, RU_USER)).startsWith("""
                🍽️<b><i>Гречка</i></b> (+200 ккал)
                <b>Б:</b> 17/100г (<b>+7</b>) | <b>Ж:</b> 7/70г (<b>+2</b>) | <b>У:</b> 70/250г (<b>+40</b>)
                <b>Вес порции:</b> 150 г
                <b>Количество порций:</b> 1

                500/2000 <b>+200 ккал</b>  (<b>+10%</b>)
                """).endsWith(" 🔥 <b>25%</b>");
    }

    @Test
    void russianCardWithoutTargetsShowsTodayTotal() {
        when(profileService.checkTargetCalculateParams(profile)).thenReturn(false);

        String card = dishTexts.buildDishCardText(dish, RU_USER);
        assertThat(card).contains("<b>Б:</b> 17г (<b>+7</b>)");
        assertThat(card).endsWith("📊 Итого за сегодня: <b>500 ккал</b>\n");
    }

    @Test
    void englishCardIsTranslated() {
        when(profileService.checkTargetCalculateParams(profile)).thenReturn(true);
        dish.setName("Buckwheat");

        String card = dishTexts.buildDishCardText(dish, EN_USER);
        assertThat(card).startsWith("🍽️<b><i>Buckwheat</i></b> (+200 kcal)\n<b>P:</b> 17/100g (<b>+7</b>)");
        assertThat(card).contains("<b>Portion weight:</b> 150 g", "500/2000 <b>+200 kcal</b>");
        assertThat(card).doesNotContainPattern("[А-Яа-яЁё]");
    }

    @Test
    void voiceTooLongShowsDurationInUserLanguage() {
        assertThat(dishTexts.getVoiceTooLongText(RU_USER, Duration.ofMinutes(2))).contains("максимум 2 мин.");
        assertThat(dishTexts.getVoiceTooLongText(RU_USER, Duration.ofSeconds(90))).contains("максимум 90 сек.");
        assertThat(dishTexts.getVoiceTooLongText(EN_USER, Duration.ofMinutes(2))).contains("the maximum is 2 min.");
    }

    // ── Дневник «Сегодня» (перенесён из Dish.getDishesString) ──────

    @Test
    void russianTodayDiaryLooksAsBefore() {
        assertThat(dishTexts.buildTodayDiaryText(RU_USER, "12 ОКТЯБРЯ 2026")).isEqualTo("""
                📖 <b>Дневник питания (12 ОКТЯБРЯ 2026)</b>

                🍽 <b>Без названия</b>
                🔥 Ккал: <b>300</b> 🥩 Б: <b>10 г</b> 🥑 Ж: <b>5 г</b> 🍞 У: <b>30 г</b>

                🍽 <b>Гречка</b>
                🔥 Ккал: <b>200</b> 🥩 Б: <b>7 г</b> 🥑 Ж: <b>2 г</b> 🍞 У: <b>40 г</b>
                🍽 Порции: <b>1</b>


                ⚡️ <b>ИТОГО:</b>
                🔥 К: <b>500</b> / 2000 ккал
                🥩 Б: <b>17</b> / 100 г
                🥑 Ж: <b>7</b> / 70 г
                🍞 У: <b>70</b> / 250 г
                """);
    }

    @Test
    void englishTodayDiaryIsTranslated() {
        dish.setName("Buckwheat");
        earlier.setName("Toast");

        String diary = dishTexts.buildTodayDiaryText(EN_USER, "12 OCTOBER 2026");
        assertThat(diary).startsWith("📖 <b>Food diary (12 OCTOBER 2026)</b>");
        assertThat(diary).contains("🍽 Portions: <b>1</b>", "⚡️ <b>TOTAL:</b>", "🔥 Cal: <b>500</b> / 2000 kcal",
                "🥩 P: <b>17</b> / 100 g");
        assertThat(diary).doesNotContainPattern("[А-Яа-яЁё]");
    }
}
