package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTextsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Карточка блюда (AnalyticsService.getInfo): русская — как до перевода, английская — без русских слов. */
class AnalyticsServiceDishCardTest {

    private static final Long RU_USER = 1L;
    private static final Long EN_USER = 2L;

    private DishService dishService;
    private UserNutritionProfileService profileService;
    private AnalyticsService analytics;

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

        analytics = new AnalyticsService(mock(WaterEntryService.class), mock(WeightEntryService.class), dishService,
                profileService, settings, new CalorieTextsService(new I18nProperties(), settings));
    }

    @Test
    void russianCardWithTargetsLooksAsBefore() {
        when(profileService.checkTargetCalculateParams(profile)).thenReturn(true);

        assertThat(analytics.getInfo(dish, RU_USER)).startsWith("""
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

        String card = analytics.getInfo(dish, RU_USER);
        assertThat(card).contains("<b>Б:</b> 17г (<b>+7</b>)");
        assertThat(card).endsWith("📊 Итого за сегодня: <b>500 ккал</b>\n");
    }

    @Test
    void englishCardIsTranslated() {
        when(profileService.checkTargetCalculateParams(profile)).thenReturn(true);
        dish.setName("Buckwheat");

        String card = analytics.getInfo(dish, EN_USER);
        assertThat(card).startsWith("🍽️<b><i>Buckwheat</i></b> (+200 kcal)\n<b>P:</b> 17/100g (<b>+7</b>)");
        assertThat(card).contains("<b>Portion weight:</b> 150 g", "500/2000 <b>+200 kcal</b>");
        assertThat(card).doesNotContainPattern("[А-Яа-яЁё]");
    }
}
