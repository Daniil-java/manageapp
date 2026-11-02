package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalyticsServiceTodayBarsTest {
    private static final Long USER_ID = 7L;

    private final WaterEntryService waterEntryService = mock(WaterEntryService.class);
    private final DishService dishService = mock(DishService.class);
    private final UserNutritionProfileService profileService = mock(UserNutritionProfileService.class);
    private final CalorieTexts texts = mock(CalorieTexts.class);
    private final AnalyticsService service = new AnalyticsService(waterEntryService, dishService, profileService, texts);

    @Test
    void dataLoadedOnceAndBarWithoutNormSkipped() {
        UserNutritionProfile profile = new UserNutritionProfile();
        profile.setCaloriesNormPerDay(2000);
        profile.setFatsNormGramsPerDay(70);
        profile.setCarbsNormGramsPerDay(250);
        // белков нет — раньше NPE прятался в catch, теперь шкалы просто нет
        when(profileService.getOrCreateProfile(USER_ID)).thenReturn(profile);
        Dish dish = new Dish();
        dish.setCalories(1000);
        dish.setFats(null);
        dish.setCarbohydrates(125);
        when(dishService.getTodayDishes(USER_ID)).thenReturn(List.of(dish));
        when(waterEntryService.getTodayTotal(USER_ID)).thenReturn(500);
        when(texts.languageOf(USER_ID)).thenReturn(AppLanguage.RU);
        when(texts.get(eq(AppLanguage.RU), anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(1));

        List<String> bars = service.buildTodayBars(USER_ID);

        assertThat(bars).hasSize(4);
        assertThat(bars.get(0)).startsWith(BotTextKeys.MACRO_LABEL_CALORIES).endsWith(" 50%");
        assertThat(bars).noneMatch(bar -> bar.startsWith(BotTextKeys.MACRO_LABEL_PROTEINS));
        assertThat(bars.get(3)).startsWith(BotTextKeys.MACRO_LABEL_WATER).endsWith(" 25%"); // 500 из 2000 по умолчанию
        verify(profileService, times(1)).getOrCreateProfile(USER_ID);
        verify(dishService, times(1)).getTodayDishes(USER_ID);
        verify(texts, times(1)).languageOf(USER_ID);
    }
}
