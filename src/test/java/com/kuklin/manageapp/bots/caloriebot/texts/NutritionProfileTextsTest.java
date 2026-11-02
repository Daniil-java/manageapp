package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Профиль питания текстом: русский — как раньше UserNutritionProfile.toTelegramView, английский — без русских слов. */
class NutritionProfileTextsTest {

    private static final Long RU_USER = 1L;
    private static final Long EN_USER = 2L;

    private NutritionProfileTexts profileTexts;

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(RU_USER)).thenReturn("ru");
        when(settings.findLanguageCode(EN_USER)).thenReturn("en");
        profileTexts = new NutritionProfileTexts(new CalorieTexts(new I18nProperties(), settings));
    }

    private static UserNutritionProfile profile(Long userId) {
        return new UserNutritionProfile().setUserId(userId)
                .setSex(UserNutritionProfile.Sex.MALE).setAgeYears(30).setHeightCm(180)
                .setCurrentWeightKg(new BigDecimal("80.50"))
                .setActivityLevel(UserNutritionProfile.ActivityLevel.MEDIUM)
                .setGoal(UserNutritionProfile.Goal.MAINTAIN)
                .setDietType(UserNutritionProfile.DietType.REGULAR)
                .setCaloriesNormPerDay(2500).setProteinsNormGramsPerDay(144)
                .setFatsNormGramsPerDay(72).setCarbsNormGramsPerDay(300)
                .setWaterTargetMlPerDay(2500);
    }

    @Test
    void russianProfileLooksAsBefore() {
        // Литры — тем же String.format, что и раньше: десятичный разделитель зависит от локали машины
        String liters = String.format("%.1f", 2.5);

        assertThat(profileTexts.buildProfileText(profile(RU_USER))).isEqualTo("""
                📋 Профиль питания
                ──────────────────────

                👤 Пол: Мужской ♂️
                🎂 Возраст: 30 лет
                📏 Рост: 180 см
                ⚖️ Текущий вес: 80.5 кг
                🏃 Активность: Средняя
                🎯 Цель: Поддержание
                🥗 Тип питания: Обычная

                🔥 Норма калорий и БЖУ
                • Калории: 2500 ккал
                • Белки: 144 г
                • Жиры: 72 г
                • Углеводы: 300 г
                Автопересчёт: норма меняется вместе с весом, активностью и целью.

                💧 Вода
                Цель по воде: 2500 мл (≈ %s л)
                """.formatted(liters));
    }

    @Test
    void russianAgeIsDeclined() {
        assertThat(profileTexts.buildProfileText(profile(RU_USER).setAgeYears(21))).contains("🎂 Возраст: 21 год");
        assertThat(profileTexts.buildProfileText(profile(RU_USER).setAgeYears(23))).contains("🎂 Возраст: 23 года");
    }

    @Test
    void manualNormsAreMarked() {
        String text = profileTexts.buildProfileText(profile(RU_USER)
                .setNormMode(UserNutritionProfile.NormMode.MANUAL)
                .setWaterMode(UserNutritionProfile.NormMode.MANUAL));

        assertThat(text).contains("• Калории: 2500 ккал ✍️ вручную", "Без пересчёта:", "мл (≈ ").contains("л) ✍️ вручную");
    }

    @Test
    void emptyProfileShowsNoData() {
        String text = profileTexts.buildProfileText(new UserNutritionProfile().setUserId(RU_USER));

        assertThat(text).contains("👤 Пол: нет данных", "🎂 Возраст: нет данных",
                "нет данных (нужно заполнить профиль)", "Цель по воде: нет данных");
    }

    @Test
    void englishProfileIsTranslated() {
        String text = profileTexts.buildProfileText(profile(EN_USER));

        assertThat(text).contains("👤 Sex: Male ♂️", "🎂 Age: 30 years", "📏 Height: 180 cm",
                "🏃 Activity: Moderate", "• Calories: 2500 kcal", "Water target: 2500 ml");
        assertThat(text).doesNotContainPattern("[А-Яа-яЁё]");
    }
}
